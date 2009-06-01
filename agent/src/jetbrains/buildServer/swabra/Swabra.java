/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.swabra;

/**
 * User: vbedrosova
 * Date: 14.04.2009
 * Time: 14:10:58
 */
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import static jetbrains.buildServer.swabra.SwabraUtil.*;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import com.intellij.openapi.util.io.FileUtil;


public class Swabra extends AgentLifeCycleAdapter {
  private Map<File, FileInfo> myFiles = new HashMap<File, FileInfo>();
  private SwabraLogger myLogger;
  private File myCheckoutDir;
  private String myMode;
  private boolean myVerbose;

  private final List<File> myAppeared = new ArrayList<File>();
  private final List<File> myModified = new ArrayList<File>();

  private static boolean isEnabled(final String currMode) {
    return BEFORE_BUILD.equals(currMode) || AFTER_BUILD.equals(currMode);
  }

  private static boolean needFullCleanup(final String prevMode) {
    return !isEnabled(prevMode);
  }

  public Swabra(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher) {
    agentDispatcher.addListener(this);
  }

  public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
    final Map<String, String> runnerParams = runningBuild.getRunnerParameters();
    myLogger = new SwabraLogger(runningBuild.getBuildLogger(), Logger.getLogger(Swabra.class));
    myCheckoutDir = runningBuild.getCheckoutDirectory();
    myVerbose = isVerbose(runnerParams);
    final String mode = getSwabraMode(runnerParams);
    try {
      if (!isEnabled(mode)) {
        myLogger.log("Swabra is disabled", false);
        if (myFiles.size() > 0) {
          myFiles.clear();
        }
        return;
      }
      logSettings(mode, myCheckoutDir.getAbsolutePath(), myVerbose);
      if (runningBuild.isCleanBuild()) {
        return;
      }
      if (needFullCleanup(myMode)) {
        myLogger.log("It is the first build with Swabra turned on - need full cleanup", false);
        // TODO: may be ask for clean build
        if (!FileUtil.delete(myCheckoutDir)) {
          myLogger.log("Unable to remove checkout directory on swabra work start", false);
        }
        return;
      }
      if (BEFORE_BUILD.equals(mode)) {
        if (AFTER_BUILD.equals(myMode)) {
          myLogger.log("Will not perform build garbage cleanup, as it occured on previous build finish", false);
        } else {
          myLogger.log("Previous build garbage cleanup is performed before build", false);
          collectGarbage(myVerbose);
        }
      } else if (AFTER_BUILD.equals(mode) && BEFORE_BUILD.equals(myMode)) {
        // mode setting changed from "before build" to "after build"
        myLogger.log("Swabra mode setting changed from \"before build\" to \"after build\", " +
                    "need to perform build garbage clean up", false);
        collectGarbage(false);
      }
    } finally {
      myMode = mode;
    }
  }

  public void beforeRunnerStart(@NotNull final AgentRunningBuild runningBuild) {
    if (!isEnabled(myMode)) return;
    myFiles.clear();
    myLogger.log("Saving checkout directory state...", false);
    saveState(myCheckoutDir);
    myLogger.log("Finished saving checkout directory state", false);
  }

  public void buildFinished(@NotNull final BuildFinishedStatus buildStatus) {
    if (AFTER_BUILD.equals(myMode)) {
      myLogger.log("Build garbage cleanup is performed after build", false);
      collectGarbage(myVerbose);
    }
  }

  private void collectGarbage(boolean verbose) {
    if (myCheckoutDir == null || !myCheckoutDir.isDirectory()) {
      myLogger.log("Will not collect build garbage, illegal checkout directory", false);
      return;
    }
    myLogger.activityStarted();
    collectGarbage(myCheckoutDir);
    if (verbose) {
      logTotals();
    }
    myLogger.activityFinished();
    myAppeared.clear();
    myModified.clear();
    myFiles.clear();
  }

  private void collectGarbage(@NotNull final File dir) {
    final File[] files = dir.listFiles();
    if (files == null || files.length == 0) return;
    for (File file : files) {
      final FileInfo info = myFiles.get(file);
      if (info == null) {
        myAppeared.add(file);
        if (file.isDirectory()) {
          //all directory content is supposed to be garbage
          collectGarbage(file);
        }
        if (!file.delete()) {
          myLogger.log("Unable to delete previous build garbage " + file.getAbsolutePath(), false);
        }
      } else if ((file.lastModified() != info.getLastModified()) ||
                  file.length() != info.getLength()) {
        myModified.add(file);
        if (file.isDirectory()) {
          //directory's content is supposed to be modified
          collectGarbage(file);
        }
      }
    }
  }

  private void logTotals() {
    String prefix = null;
    if (myAppeared.size() > 0) {
      prefix = "Deleting ";
      for (File file : myAppeared) {
        myLogger.log(prefix + file.getAbsolutePath(), myVerbose);
      }
    }
    if (myModified.size() > 0) {
      prefix = "Detected modified ";
      for (File file : myModified) {
        myLogger.log(prefix + file.getAbsolutePath(), myVerbose);
      }
    }
    if (prefix == null) {
      myLogger.log("No garbage or modified files detected", myVerbose);
    }
  }

  private void saveState(@NotNull final File dir) {
    final File[] files = dir.listFiles();
    if (files == null || files.length == 0) return;
    for (File file : files) {
        myFiles.put(file, new FileInfo(file));
      if (file.isDirectory()) {
        saveState(file);
      }
    }
  }

  private void logSettings(String mode, String checkoutDir, boolean verbose) {
    myLogger.log("Swabra settings: mode = '" + mode +
                "', checkoutDir = " + checkoutDir +
                "', verbose = '" + verbose + "'.", false);
  }

  private static final class FileInfo {
    private final long myLength;
    private final long myLastModified;

    public FileInfo(File f) {
      myLastModified = f.lastModified();
      myLength = f.length();
    }

    public long getLastModified() {
      return myLastModified;
    }

    public long getLength() {
      return myLength;
    }
  }
}
