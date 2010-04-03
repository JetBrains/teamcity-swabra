/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.swabra.processes.HandleOutputReader;
import jetbrains.buildServer.swabra.processes.HandlePidsProvider;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.processes.ProcessExecutor;
import jetbrains.buildServer.swabra.snapshots.FilesCollectionProcessor;
import jetbrains.buildServer.swabra.snapshots.FilesCollectionProcessorForTests;
import jetbrains.buildServer.swabra.snapshots.FilesCollector;
import jetbrains.buildServer.swabra.snapshots.SnapshotGenerator;
import jetbrains.buildServer.util.EventDispatcher;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static jetbrains.buildServer.swabra.SwabraUtil.*;


public final class Swabra extends AgentLifeCycleAdapter {
  public static final String CACHE_KEY = "swabra";

  public static final String SNAPSHOT_SUFFIX = ".snapshot";

  public static final String HANDLE_PATH_SUFFIX = File.separator + "handle.exe";
  public static final String HANDLE_EXE_SYSTEM_PROP = "handle.exe.path";

  public static final String TEST_LOG = "swabra.test.log";

  private SwabraLogger myLogger;
  @NotNull
  private SmartDirectoryCleaner myDirectoryCleaner;
//  @NotNull
//  private ProcessTerminator myProcessTerminator;

  private SwabraPropertiesProcessor myPropertiesProcessor;
  private FilesCollector myFilesCollector;

  private String myMode;

  private File myCheckoutDir;
  private File myTempDir;

  private Map<File, Thread> myPrevThreads = new HashMap<File, Thread>();
  private String myHandlePath;
  private boolean myLockingProcessesDetection;

  private static boolean isEnabled(final String mode) {
    return BEFORE_BUILD.equals(mode) || AFTER_BUILD.equals(mode);
  }

  public Swabra(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher,
                @NotNull final SmartDirectoryCleaner directoryCleaner/*,
                @NotNull ProcessTerminator processTerminator*/) {
    agentDispatcher.addListener(this);
    myDirectoryCleaner = directoryCleaner;
//    myProcessTerminator = processTerminator;
  }

  @Override
  public void agentStarted(@NotNull BuildAgent agent) {
    myLogger = new SwabraLogger(Logger.getLogger(Swabra.class));
    myTempDir = agent.getConfiguration().getCacheDirectory(CACHE_KEY);
    myTempDir.mkdirs();

    myPropertiesProcessor = new SwabraPropertiesProcessor(myTempDir, myLogger);
    myPropertiesProcessor.cleanupProperties(agent.getConfiguration().getWorkDirectory().listFiles());
  }

  @Override
  public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
    final File checkoutDir = runningBuild.getCheckoutDirectory();
    waitForUnfinishedThreads(checkoutDir);

    final BuildProgressLogger buildLogger = runningBuild.getBuildLogger();
    myLogger.setBuildLogger(buildLogger);
    myCheckoutDir = checkoutDir;

    final Map<String, String> runnerParams = runningBuild.getRunnerParameters();
    myMode = getSwabraMode(runnerParams);
    final boolean verbose = isVerbose(runnerParams);
    final boolean strict = isStrict(runnerParams);
    final boolean kill = isKill(runnerParams);
    myLockingProcessesDetection = isLockingProcessesDetection(runnerParams);

    logSettings(myMode, myCheckoutDir.getAbsolutePath(),
      kill, strict, myLockingProcessesDetection, verbose);

    if (kill || myLockingProcessesDetection) {
      prepareHandle();
    } else {
      myHandlePath = null;
    }

    myPropertiesProcessor.readProperties();

    if (!isEnabled(myMode)) {
      myLogger.message("Swabra is disabled", false);
      myPropertiesProcessor.markDirty(myCheckoutDir);
      return;
    }

    myFilesCollector = initFilesCollector(buildLogger, verbose, kill);

    final File snapshot;
    try {
      if (runningBuild.isCleanBuild()) {
        myLogger.swabraDebug("Clean build. Nothing to do");
        return;
      }
      if (myPropertiesProcessor.isDirty(myCheckoutDir)) {
        if (strict) {
          doCleanup(myCheckoutDir, strict);
        } else {
          myLogger.swabraDebug("Checkout dir is dirty, but strict mode disabled. Do nothing");
        }
        return;
      }
      if (myPropertiesProcessor.isClean(myCheckoutDir)) {
        if (BEFORE_BUILD.equals(myMode)) {
          myLogger.swabraDebug("Will not perform files cleanup, directory is supposed to be clean from newly created and modified files");
        }
        return;
      }
      snapshot = getSnapshot();
    } finally {
      myPropertiesProcessor.deleteRecord(myCheckoutDir);
    }

    final FilesCollector.CollectionResult result = myFilesCollector.collect(snapshot, myCheckoutDir);

    if (!strict) {
      myLogger.swabraDebug("Strict mode is disabled. Skipping collection result: " + result);
      return;
    }

    switch (result) {
      case FAILURE:
        doCleanup(myCheckoutDir, strict);
        return;

      case RETRY:
        myPropertiesProcessor.markDirty(myCheckoutDir);
        myMode = null;
        fail();
    }
  }

  @Override
  public void sourcesUpdated(@NotNull AgentRunningBuild runningBuild) {
    if (!isEnabled(myMode)) return;
    makeSnapshot();
  }

  @Override
  public void beforeRunnerStart(@NotNull final AgentRunningBuild runningBuild) {
    if (!isEnabled(myMode)) return;
    if (!runningBuild.isCheckoutOnAgent() && !runningBuild.isCheckoutOnServer()) {
      makeSnapshot();
    }
  }

  @Override
  public void beforeBuildFinish(@NotNull final BuildFinishedStatus buildStatus) {
    if (!isEnabled(myMode)) return;
    myLogger.activityStarted();
    try {
      if (myLockingProcessesDetection) {
        final ExecResult result = ProcessExecutor.runHandleAcceptEula(myHandlePath, myCheckoutDir.getAbsolutePath());
        if (HandleOutputReader.noResult(result.getStdout())) {
          myLogger.swabraMessage("No processes lock the checkout directory", true);
        } else {
          myLogger.message("The following processes lock the checkout directory", true);
          HandleOutputReader.read(result.getStdout(), new HandleOutputReader.LineProcessor() {
            public void processLine(@NotNull String line) {
              if (line.contains(myCheckoutDir.getAbsolutePath())) {
                myLogger.warn(line);
              }
            }
          });
        }
      }
      if (AFTER_BUILD.equals(myMode)) {
        myLogger.swabraMessage("Build files cleanup will be performed after build", true);
      }
    } finally {
      myLogger.activityFinished();
    }
  }

  @Override
  public void buildFinished(@NotNull final BuildFinishedStatus buildStatus) {
    if (AFTER_BUILD.equals(myMode)) {
      final Thread t = new Thread(new Runnable() {
        public void run() {
          final FilesCollector.CollectionResult result =
            myFilesCollector.collect(getSnapshot(), myCheckoutDir);

          switch (result) {
            case FAILURE:
              myPropertiesProcessor.markDirty(myCheckoutDir);
              break;

            case RETRY:
              myPrevThreads.remove(myCheckoutDir);
              break;

            case SUCCESS:
              myPropertiesProcessor.markClean(myCheckoutDir);
              break;
          }
        }
      });
      myPrevThreads.put(myCheckoutDir, t);
      t.start();
    }
  }

  private FilesCollector initFilesCollector(BuildProgressLogger buildLogger, boolean verbose, boolean kill) {
    final LockedFileResolver lockedFileResolver =
      (!kill || myHandlePath == null) ?
        null : new LockedFileResolver(new HandlePidsProvider(myHandlePath), /*myProcessTerminator,*/ buildLogger);

    final FilesCollectionProcessor processor = (System.getProperty(TEST_LOG) == null) ?
      new FilesCollectionProcessor(myLogger, lockedFileResolver, verbose, kill) :
      new FilesCollectionProcessorForTests(myLogger, lockedFileResolver, verbose, true, System.getProperty(TEST_LOG));

    return new FilesCollector(processor, myLogger);
  }

  private void makeSnapshot() {
    final String snapshotName = Integer.toHexString(myCheckoutDir.hashCode());
    if (!new SnapshotGenerator(myCheckoutDir, myTempDir, myLogger).generateSnapshot(snapshotName)) {
      myPropertiesProcessor.markDirty(myCheckoutDir);
      myMode = null;
    } else {
      myPropertiesProcessor.setSnapshot(myCheckoutDir, snapshotName);
    }
  }

  private File getSnapshot() {
    return new File(myTempDir, myPropertiesProcessor.getSnapshot(myCheckoutDir) + SNAPSHOT_SUFFIX);
  }

  private void logSettings(String mode, String checkoutDir,
                           boolean kill, boolean strict,
                           boolean lockingProcessesDetectionEnabled,
                           boolean verbose) {
    myLogger.debug("Swabra settings: mode = '" + mode +
      "', checkoutDir = " + checkoutDir +
      "', kill = " + kill +
      "', strict = " + strict +
      "', locking processes detection = " + lockingProcessesDetectionEnabled +
      "', verbose = '" + verbose + "'.");
  }

  private void waitForUnfinishedThreads(@NotNull File checkoutDir) {
    final Thread t = myPrevThreads.get(checkoutDir);
    if ((t != null) && t.isAlive()) {
      myLogger.message("Waiting for Swabra to cleanup previous build files", true);
      try {
        t.join();
      } catch (InterruptedException e) {
        myLogger.swabraWarn("Interrupted while waiting for previous build files cleanup");
      }
    }
    myPrevThreads.remove(checkoutDir);
  }

  private void doCleanup(File checkoutDir, boolean strict) {
    myLogger.activityStarted();
    myDirectoryCleaner.cleanFolder(checkoutDir, new SmartDirectoryCleanerCallback() {
      public void logCleanStarted(File dir) {
        myLogger.message("Need a clean snapshot of checkout directory - forcing clean checkout for " + dir, true);
      }

      public void logFailedToDeleteEmptyDirectory(File dir) {
        myLogger.error("Failed to delete empty directory " + dir.getAbsolutePath());
      }

      public void logFailedToCleanFilesUnderDirectory(File dir) {
        myLogger.error("Failed to delete files in directory " + dir.getAbsolutePath());

      }

      public void logFailedToCleanFile(File file) {
        myLogger.error("Failed to delete file " + file.getAbsolutePath());
      }

      public void logFailedToCleanEntireFolder(File dir) {
        myLogger.error("Failed to delete directory " + dir.getAbsolutePath());
        myPropertiesProcessor.markDirty(myCheckoutDir);
        myMode = null;
      }
    });
    myLogger.activityFinished();
    if (strict && myMode == null) {
      //cleanup failed
      fail();
    }
  }

  private static String unifyPath(String path) {
    return path.replace("/", File.separator).replace("\\", File.separator);
  }

  private static boolean notDefined(String value) {
    return (value == null) || ("".equals(value));
  }

  private void fail() {
    myLogger.message("##teamcity[buildStatus status='FAILURE' text='Swabra failed cleanup: some files are locked']", true);
  }

  private void prepareHandle() {
    myHandlePath = System.getProperty(HANDLE_EXE_SYSTEM_PROP);
    if (notDefined(myHandlePath)) {
      myLogger.swabraWarn("Handle path not defined");
      myHandlePath = null;
      return;
    }
    if (!unifyPath(myHandlePath).endsWith(HANDLE_PATH_SUFFIX)) {
      myLogger.swabraWarn("Handle path must end with: " + HANDLE_PATH_SUFFIX);
      myHandlePath = null;
      return;
    }
    final File handleFile = new File(myHandlePath);
    if (!handleFile.isFile()) {
      myLogger.swabraWarn("No Handle executable found at " + myHandlePath);
      myHandlePath = null;
    }
  }
}