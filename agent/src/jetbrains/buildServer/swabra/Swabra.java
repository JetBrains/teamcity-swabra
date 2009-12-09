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
import jetbrains.buildServer.swabra.processes.HandlePidsProvider;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.processes.ProcessExecutor;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;


public final class  Swabra extends AgentLifeCycleAdapter {
  public static final String CACHE_KEY = "swabra";
  public static final String WORK_DIR_PROP = "agent.work.dir";
  public static final String HANDLE_EXE = "handle.exe";

  private SwabraLogger myLogger;
  private SmartDirectoryCleaner myDirectoryCleaner;

  private SwabraPropertiesProcessor myPropertiesProcessor;
  private Snapshot mySnapshot;

  private String myMode;
  private boolean myVerbose;

  private File myCheckoutDir;

  private Map<File, Thread> myPrevThreads = new HashMap<File, Thread>();
  private String myHandlePath;

  private static boolean isEnabled(final String mode) {
    return BEFORE_BUILD.equals(mode) || AFTER_BUILD.equals(mode);
  }

  public Swabra(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher,
                @NotNull final SmartDirectoryCleaner directoryCleaner) {
    agentDispatcher.addListener(this);
    myDirectoryCleaner = directoryCleaner;
  }

  public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
    final BuildProgressLogger buildLogger = runningBuild.getBuildLogger();
    final SwabraLogger logger = new SwabraLogger(buildLogger, Logger.getLogger(Swabra.class));
    final File checkoutDir = runningBuild.getCheckoutDirectory();
    waitForUnfinishedThreads(checkoutDir, logger);

    myLogger = logger;
    myCheckoutDir = checkoutDir;
    final Map<String, String> runnerParams = runningBuild.getRunnerParameters();
    myMode = getSwabraMode(runnerParams);
    myVerbose = isVerbose(runnerParams);
    final boolean strict = isStrict(runnerParams);
    final File tempDir = runningBuild.getAgentConfiguration().getCacheDirectory(CACHE_KEY);

    myPropertiesProcessor = new SwabraPropertiesProcessor(tempDir, myLogger);
    myPropertiesProcessor.readProperties();

    myHandlePath = getHandlePath(runnerParams);
    logSettings(myMode, myCheckoutDir.getAbsolutePath(), strict, myHandlePath, myVerbose);

    if (!isEnabled(myMode)) {
      myLogger.message("Swabra is disabled", false);
      myPropertiesProcessor.markDirty(myCheckoutDir);
      myPropertiesProcessor.writeProperties();
      return;
    }

    final LockedFileResolver lockedFileResolver =
      ((myHandlePath == null) || (!unifyPath(myHandlePath).endsWith(File.separator + HANDLE_EXE))) ?
        null : new LockedFileResolver(new HandlePidsProvider(myHandlePath, buildLogger), buildLogger);
    mySnapshot = new Snapshot(tempDir, myCheckoutDir, lockedFileResolver, strict);

    String snapshotName;
    try {
      if (runningBuild.isCleanBuild()) {
        return;
      }
      if (myPropertiesProcessor.isDirty(myCheckoutDir)) {
        doCleanup(myCheckoutDir);
        return;
      }
      if (myPropertiesProcessor.isClean(myCheckoutDir)) {
        if (BEFORE_BUILD.equals(myMode)) {
          myLogger.debug("Swabra: Will not perform files cleanup, directory is supposed to be clean from newly created and modified files");
        }
        return;
      }
      snapshotName = myPropertiesProcessor.getSnapshot(myCheckoutDir);
    } finally {
      myPropertiesProcessor.deleteRecord(myCheckoutDir);
      myPropertiesProcessor.writeProperties();      
    }

    myLogger.debug("Swabra: Previous build files cleanup is performed before build");
    if (!mySnapshot.collect(snapshotName, myLogger, myVerbose)) {
      myPropertiesProcessor.markDirty(myCheckoutDir);
      myPropertiesProcessor.writeProperties();
      myMode = null;
      if (strict) {
        fail();
      }
    }
  }

  public void beforeRunnerStart(@NotNull final AgentRunningBuild runningBuild) {
    if (!isEnabled(myMode)) return;
    final String snapshotName = "" + myCheckoutDir.hashCode();
    if (!mySnapshot.snapshot(snapshotName, myLogger, myVerbose)) {
      myPropertiesProcessor.markDirty(myCheckoutDir);
      myPropertiesProcessor.writeProperties();
      myMode = null;
    } else {
      myPropertiesProcessor.setSnapshot(myCheckoutDir, snapshotName);
      myPropertiesProcessor.writeProperties();      
    }
  }

  public void beforeBuildFinish(@NotNull final BuildFinishedStatus buildStatus) {
    if (myHandlePath != null) {
      final String params[] = {myCheckoutDir.getAbsolutePath()};
      ProcessExecutor.run(myHandlePath, params, myLogger.getBuildLogger());
    }
    if (AFTER_BUILD.equals(myMode)) {
      myLogger.message("Swabra: Build files cleanup will be performed after build", true);
    }
  }
  
  public void buildFinished(@NotNull final BuildFinishedStatus buildStatus) {
    if (AFTER_BUILD.equals(myMode)) {
      myLogger.debug("Swabra: Build files cleanup is performed after build");
      final Thread t = new Thread(new Runnable() {
        public void run() {
          if (!mySnapshot.collect(myPropertiesProcessor.getSnapshot(myCheckoutDir), myLogger, myVerbose)) {
            myPrevThreads.remove(myCheckoutDir);
          } else {
            myPropertiesProcessor.markClean(myCheckoutDir);            
            myPropertiesProcessor.writeProperties();
          }
        }
      });
      myPrevThreads.put(myCheckoutDir, t);
      t.start();
    }
  }

  private void logSettings(String mode, String checkoutDir, boolean strict, String handlePath, boolean verbose) {
    myLogger.debug("Swabra settings: mode = '" + mode +
      "', checkoutDir = " + checkoutDir +
      "', strict = " + strict +
      "', handle path = " + handlePath +
      "', verbose = '" + verbose + "'.");
  }

  private void waitForUnfinishedThreads(@NotNull File checkoutDir, @NotNull SwabraLogger logger) {
    final Thread t = myPrevThreads.get(checkoutDir);
    if ((t != null) && t.isAlive()) {
      logger.message("Waiting for Swabra to cleanup previous build files", true);
      try {
        t.join();
      } catch (InterruptedException e) {
        logger.error("Swabra: Interrupted while waiting for previous build files cleanup");
        logger.exception(e, true);
      }
    }
    myPrevThreads.remove(checkoutDir);
  }

  private void doCleanup(@NotNull File checkoutDir) {
    myDirectoryCleaner.cleanFolder(checkoutDir, new SmartDirectoryCleanerCallback() {
      public void logCleanStarted(File dir) {
        myLogger.warn("Swabra: Need a valid checkout directory snapshot - forcing clean checkout for " + dir);
      }

      public void logFailedToDeleteEmptyDirectory(File dir) {
        myLogger.warn("Swabra: Failed to delete empty checkout directory " + dir.getAbsolutePath());
      }

      public void logFailedToCleanFilesUnderDirectory(File dir) {
        myLogger.warn("Swabra: Failed to delete files in directory " + dir.getAbsolutePath());

      }

      public void logFailedToCleanFile(File file) {
        myLogger.warn("Swabra: Failed to delete file " + file.getAbsolutePath());
      }

      public void logFailedToCleanEntireFolder(File dir) {
        myLogger.warn("Swabra: Failed to delete directory " + dir.getAbsolutePath());
        myPropertiesProcessor.markDirty(myCheckoutDir);
        myPropertiesProcessor.writeProperties();
        myMode = null;        
      }
    });
  }

  private void fail() {
    myLogger.message("##teamcity[buildStatus status='FAILURE' text='Swabra failed cleanup']", true);
  }

  private String unifyPath(String path) {
    return path.replace("/", File.separator).replace("\\", File.separator);   
  }
}
