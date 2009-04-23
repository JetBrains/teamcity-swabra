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
import jetbrains.buildServer.log.Loggers;
import org.jetbrains.annotations.NotNull;
import static jetbrains.buildServer.swabra.SwabraUtil.*;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;


public class Swabra extends AgentLifeCycleAdapter {
  public static final Logger LOGGER = Loggers.AGENT;

  private Map<File, FileInfo> myFiles = new HashMap<File, FileInfo>();
  private BuildProgressLogger myLogger;
  private File myCheckoutDir;
  private String myMode;

  private final List<File> myAppeared = new ArrayList<File>();
  private final List<File> myModified = new ArrayList<File>();


  public Swabra(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher) {
    agentDispatcher.addListener(this);
  }

  private static boolean needFullCleanup(final String prevMode) {
    return !isEnabled(prevMode);
  }

  private static boolean isEnabled(final String currMode) {
    return BEFORE_BUILD.equals(currMode) || AFTER_BUILD.equals(currMode);
  }
  public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
    final Map<String, String> runnerParams = runningBuild.getRunnerParameters();
    myLogger = runningBuild.getBuildLogger();
    myCheckoutDir = runningBuild.getCheckoutDirectory();
    final String mode = getSwabraMode(runnerParams);
    try {
      if (!isEnabled(mode)) return;
      if (needFullCleanup(myMode) || runningBuild.isCleanBuild()) {
        FileUtil.delete(runningBuild.getCheckoutDirectory()); // TODO: may be ask for clean build
        myFiles.clear();
        return;
      }
      if (BEFORE_BUILD.equals(mode)) {
        message("Previous build garbage cleanup is performed before build");
        collectGarbage(true); // TODO: pass verbose option
      } else if (AFTER_BUILD.equals(mode) && BEFORE_BUILD.equals(myMode)) {
        // mode setting changed from "before build" to "after build"
        collectGarbage(false);
      }
    } finally {
      myMode = mode;
    }
  }

  public void beforeBuildFinished(@NotNull final BuildFinishedStatus buildStatus) {
    if (AFTER_BUILD.equals(myMode)) {
      message("Build garbage cleanup is performed after build");
      collectGarbage(true); // TODO: pass verbose option
    }
  }

  private void collectGarbage(boolean verbose) {
    if (myCheckoutDir == null || !myCheckoutDir.isDirectory()) return;
    collectGarbage(myCheckoutDir);
    String target = null;
    if (myAppeared.size() > 0) {
      target = "Build garbage";
      myLogger.targetStarted(target);
      for (File file : myAppeared) {
        message(file.getAbsolutePath());
      }
      myLogger.targetFinished(target);
    }

    if (myModified.size() > 0) {
      target = "Modified";
      myLogger.targetStarted(target);
      for (File file : myModified) {
        message(file.getAbsolutePath());
      }
      myLogger.targetFinished(target);
    }
    if (target == null) {
      message("No garbage or modified files detected");
    }
    myAppeared.clear();
    myModified.clear();
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
          warning("Unable to delete previous build garbage " + file.getAbsolutePath());
        }
      } else if (file.lastModified() > info.getLastModified()) {
        myModified.add(file);
        if (file.isDirectory()) {
          //directory's content is supposed to be modified
          collectGarbage(file);
        }
      }
    }
  }

  public void beforeRunnerStart(@NotNull final AgentRunningBuild runningBuild) {
    if (!isEnabled(myMode)) return;
    saveState(myCheckoutDir);
  }

  private void saveState(@NotNull final File dir) {
    final File[] files = dir.listFiles();
    if (files == null || files.length == 0) return;
    for (File file : files) {
      final FileInfo oldFileInfo = myFiles.get(file);
      final long newLastModified = file.lastModified();
      if (oldFileInfo == null) {
        myFiles.put(file, new FileInfo(newLastModified));
      } else if (newLastModified > oldFileInfo.getLastModified()) {
        oldFileInfo.setLastModified(newLastModified);
      }
      if (file.isDirectory()) {
        saveState(file);
      }
    }
  }

  private void message(@NotNull final String message) {
    System.out.println(message);
    LOGGER.debug(message);
    myLogger.message(message);
  }

  private void warning(@NotNull final String message) {
    System.out.println(message);
    LOGGER.debug(message);
    myLogger.warning(message);
  }

  private static final class FileInfo {
    private long myLastModified;

    public FileInfo(long lastModified) {
      myLastModified = lastModified;
    }

    public long getLastModified() {
      return myLastModified;
    }

    public void setLastModified(long lastModified) {
      myLastModified = lastModified;
    }
  }
}
