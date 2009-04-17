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

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import com.intellij.openapi.diagnostic.Logger;


public class Swabra extends AgentLifeCycleAdapter {
  public static final Logger LOGGER = Loggers.AGENT;

  private Map<File, FileInfo> myFiles = new HashMap<File, FileInfo>();
  private BuildProgressLogger myLogger;
  private File myCheckoutDir;

  private boolean myEnabled;

  private final List<File> myAppeared = new ArrayList<File>();
  private final List<File> myModified = new ArrayList<File>();


  public Swabra(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher) {
    agentDispatcher.addListener(this);
  }

  public void agentStarted(@NotNull final BuildAgent agent) {
    myFiles.clear(); // TODO: may be read from file? 
  }

  public void checkoutDirectoryRemoved(@NotNull final File checkoutDir) {
    myFiles.clear();
  }

  public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
    myEnabled = SwabraUtil.isSwabraEnabled(runningBuild.getRunnerParameters());
    myLogger = runningBuild.getBuildLogger();
    myCheckoutDir = runningBuild.getCheckoutDirectory();
    if (myFiles.size() == 0) return;
    if (!myEnabled || runningBuild.isCleanBuild() || myCheckoutDir == null || !myCheckoutDir.isDirectory()) {
      myFiles.clear();
      return;
    }
    message("Collecting garbage from previous build");
    collectGarbage(myCheckoutDir);
    
    String target = null;
    if (myAppeared.size() > 0) {
      target = "Garbage from previous build";
      myLogger.targetStarted(target);
      for (File file : myAppeared) {
        message(file.getAbsolutePath());
      }
      myLogger.targetFinished(target);
    }

    if (myModified.size() > 0) {
      target = "Modified during previous build";
      myLogger.targetStarted(target);
      for (File file : myModified) {
        message(file.getAbsolutePath());
      }
      myLogger.targetFinished(target);
    }
    if (target == null) {
      message("No garbage detected");      
    }
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
    if (!myEnabled || (myFiles.size() == 0 && !runningBuild.isCleanBuild())) return;
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
