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
import java.util.Map;

import static jetbrains.buildServer.swabra.SwabraUtil.*;


public final class Swabra extends AgentLifeCycleAdapter {
  public static final String CACHE_KEY = "swabra";

  public static final String SNAPSHOT_SUFFIX = ".snapshot";
  public static final String NON_STRICT_MARK = "*";

  public static final String HANDLE_PATH_SUFFIX = File.separator + "handle.exe";
  public static final String HANDLE_EXE_SYSTEM_PROP = "handle.exe.path";

  public static final String TEST_LOG = "swabra.test.log";

  private SwabraLogger myLogger;
  @NotNull
  private SmartDirectoryCleaner myDirectoryCleaner;
//  @NotNull
//  private ProcessTerminator myProcessTerminator;

  private SwabraPropertiesProcessor myPropertiesProcessor;

  //  private String myMode;
  private boolean myEnabled;
  private boolean myStrict;
  private boolean myLockingProcessesDetection;
  private String myHandlePath;

  private File myCheckoutDir;
  private File myTempDir;


//  private Map<File, Thread> myPrevThreads = new HashMap<File, Thread>();

//  private static boolean isEnabled(final String mode) {
//    return BEFORE_BUILD.equals(mode) || AFTER_BUILD.equals(mode);
//  }

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
//    waitForUnfinishedThreads(checkoutDir);
    myLogger.setBuildLogger(runningBuild.getBuildLogger());
    myCheckoutDir = runningBuild.getCheckoutDirectory();

    final Map<String, String> runnerParams = runningBuild.getRunnerParameters();
//    myMode = getSwabraMode(runnerParams);
    myEnabled = isSwabraEnabled(runnerParams);
    myStrict = isStrict(runnerParams);
    final boolean kill = isKill(runnerParams);
    final boolean verbose = isVerbose(runnerParams);
    myLockingProcessesDetection = isLockingProcessesDetection(runnerParams);

    logSettings(myEnabled, myCheckoutDir, kill, myStrict, myLockingProcessesDetection, verbose);

    if (kill || myLockingProcessesDetection) {
      prepareHandle();
    } else {
      myHandlePath = null;
    }

    String snapshotName;
    try {
      if (!myEnabled) {
        myLogger.message("Swabra is disabled", false);
        return;
      }

      if (runningBuild.isCleanBuild()) {
        myLogger.swabraDebug("Clean build. No need to cleanup");
        return;
      }

      snapshotName = myPropertiesProcessor.getSnapshot(myCheckoutDir);
    } finally {
      myPropertiesProcessor.deleteRecord(myCheckoutDir);
    }
    if (snapshotName == null) {
      myLogger.swabraDebug("No snapshot saved in " + myPropertiesProcessor.getPropertiesFile().getAbsolutePath() + " for this configuration. Will force clean checkout");
      doCleanup(myCheckoutDir);
      return;
    }
    if (snapshotName.endsWith(NON_STRICT_MARK)) {
      snapshotName = snapshotName.substring(0, snapshotName.length() - 1);
      if (myStrict) {
        myLogger.swabraDebug("Snapshot " + snapshotName + " was saved without \"Ensure clean checlout\" mode. Will force clean checkout");
        doCleanup(myCheckoutDir);
        return;
      }
    }
    final FilesCollector filesCollector = initFilesCollector(verbose, kill);
    final FilesCollector.CollectionResult result = filesCollector.collect(new File(myTempDir, snapshotName), myCheckoutDir);

    switch (result) {
      case ERROR:
        myLogger.swabraDebug("Some error occurred during files collecting. Will force clean checkout");
        doCleanup(myCheckoutDir);
        return;

      case DIRTY:
        if (myStrict) {
          myLogger.swabraDebug("Checkout directory contains modified files or some files were deleted. Will force clean checkout");
          doCleanup(myCheckoutDir);
        }
        return;

      case LOCKED:
        fail();
    }
  }

  @Override
  public void sourcesUpdated(@NotNull AgentRunningBuild runningBuild) {
    if (!myEnabled) return;
    makeSnapshot();
  }

  @Override
  public void beforeRunnerStart(@NotNull final AgentRunningBuild runningBuild) {
    if (!myEnabled) return;
    if (!runningBuild.isCheckoutOnAgent() && !runningBuild.isCheckoutOnServer()) {
      makeSnapshot();
    }
  }

  @Override
  public void beforeBuildFinish(@NotNull final BuildFinishedStatus buildStatus) {
    if (myLockingProcessesDetection && myHandlePath != null) {
      myLogger.activityStarted();

      try {
        final ExecResult result = ProcessExecutor.runHandleAcceptEula(myHandlePath, myCheckoutDir.getAbsolutePath());
        if (HandleOutputReader.noResult(result.getStdout())) {
          myLogger.message("No processes lock the checkout directory", true);
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
      } finally {
        myLogger.activityFinished();
      }
    }
  }

  private FilesCollector initFilesCollector(boolean verbose, boolean kill) {
    final LockedFileResolver lockedFileResolver =
      (!kill || myHandlePath == null) ?
        null : new LockedFileResolver(new HandlePidsProvider(myHandlePath)/*, myProcessTerminator,*/);

    final FilesCollectionProcessor processor = (System.getProperty(TEST_LOG) == null) ?
      new FilesCollectionProcessor(myLogger, lockedFileResolver, verbose, kill) :
      new FilesCollectionProcessorForTests(myLogger, lockedFileResolver, verbose, true, System.getProperty(TEST_LOG));

    return new FilesCollector(processor, myLogger);
  }

  private void makeSnapshot() {
    final String snapshotName = Integer.toHexString(myCheckoutDir.hashCode());
    if (!new SnapshotGenerator(myCheckoutDir, myTempDir, myLogger).generateSnapshot(snapshotName)) {
      myEnabled = false;
    } else {
      myPropertiesProcessor.setSnapshot(myCheckoutDir, snapshotName + SNAPSHOT_SUFFIX + (myStrict ? "" : NON_STRICT_MARK));
    }
  }

  private void logSettings(boolean enabled, File checkoutDir,
                           boolean kill, boolean strict,
                           boolean lockingProcessesDetectionEnabled,
                           boolean verbose) {
    myLogger.debug("Swabra settings: enabled = '" + enabled +
      "', checkoutDir = " + checkoutDir.getAbsolutePath() +
      "', kill = " + kill +
      "', strict = " + strict +
      "', locking processes detection = " + lockingProcessesDetectionEnabled +
      "', verbose = '" + verbose + "'.");
  }

  private void doCleanup(File checkoutDir) {
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
        fail();
      }
    });
    myLogger.activityFinished();
  }

  private static String unifyPath(String path) {
    return path.replace("/", File.separator).replace("\\", File.separator);
  }

  private void fail() {
    myEnabled = false;
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

  private static boolean notDefined(String value) {
    return (value == null) || ("".equals(value));
  }
}