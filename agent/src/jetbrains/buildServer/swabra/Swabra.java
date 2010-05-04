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

  private static final String HANDLE_PATH_SUFFIX = File.separator + "handle.exe";
  private static final String HANDLE_EXE_SYSTEM_PROP = "handle.exe.path";

  public static final String TEST_LOG = "swabra.test.log";

  private SwabraLogger myLogger;
  @NotNull
  private final SmartDirectoryCleaner myDirectoryCleaner;
//  @NotNull
//  private ProcessTerminator myProcessTerminator;

  private SwabraPropertiesProcessor myPropertiesProcessor;
  private LockedFileResolver myLockedFileResolver;

  //  private String myMode;
  private boolean myCleanupEnabled;
  private boolean myStrict;
  private boolean myLockingProcessesKill;
  private boolean myLockingProcessesReport;
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
    myPropertiesProcessor.cleanupPropertiesAndSnapshots(agent.getConfiguration().getWorkDirectory().listFiles());
  }

  @Override
  public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
//    waitForUnfinishedThreads(checkoutDir);
    myLogger.setBuildLogger(runningBuild.getBuildLogger());
    myCheckoutDir = runningBuild.getCheckoutDirectory();

    final Map<String, String> runnerParams = runningBuild.getRunnerParameters();
//    myMode = getSwabraMode(runnerParams);
    myCleanupEnabled = isCleanupEnabled(runnerParams);
    myStrict = isStrict(runnerParams);
    myLockingProcessesKill = isLockingProcessesKill(runnerParams);
    myLockingProcessesReport = isLockingProcessesReport(runnerParams);
    final boolean verbose = isVerbose(runnerParams);

    logSettings(myCleanupEnabled, myCheckoutDir, myLockingProcessesKill, myStrict, myLockingProcessesReport, verbose);

    if (myLockingProcessesKill || myLockingProcessesReport) {
      prepareHandle();
    } else {
      myHandlePath = null;
    }

    myLockedFileResolver = myLockingProcessesKill && myHandlePath != null ?
      new LockedFileResolver(new HandlePidsProvider(myHandlePath)/*, myProcessTerminator,*/) : null;

    String snapshotName;
    try {
      if (!myCleanupEnabled) {
        myLogger.message("Swabra cleanup is disabled", false);
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
    if (myPropertiesProcessor.isMarkedSnapshot(snapshotName)) {
      if (myStrict) {
        myLogger.swabraDebug("Snapshot " + snapshotName + " was saved without \"Ensure clean checlout\" mode. Will force clean checkout");
        doCleanup(myCheckoutDir);
        return;
      }
      snapshotName = myPropertiesProcessor.getNonMarkedSnapshotName(snapshotName);
    }

    final FilesCollector filesCollector = initFilesCollector(verbose);
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
    if (!myCleanupEnabled) return;
    makeSnapshot();
  }

  @Override
  public void beforeRunnerStart(@NotNull final AgentRunningBuild runningBuild) {
    if (!myCleanupEnabled) return;
    if (!runningBuild.isCheckoutOnAgent() && !runningBuild.isCheckoutOnServer()) {
      makeSnapshot();
    }
  }

  @Override
  public void beforeBuildFinish(@NotNull final BuildFinishedStatus buildStatus) {
    if (myHandlePath == null) return;
    if (myLockingProcessesReport || !myCleanupEnabled && myLockedFileResolver != null) {
      myLogger.activityStarted();

      try {
        final ExecResult result = ProcessExecutor.runHandleAcceptEula(myHandlePath, myCheckoutDir.getAbsolutePath());
        if (HandleOutputReader.noResult(result.getStdout())) {
          myLogger.message("No processes lock the checkout directory", true);
        } else if (HandleOutputReader.noAdministrativeRights(result.getStdout())) {
          myLogger.message("Not enough rights to run handle.exe. Administrative privilege is required.", true);
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
        if (myLockingProcessesKill) {
          myLockedFileResolver.resolve(myCheckoutDir, true);
        }
      } finally {
        myLogger.activityFinished();
      }
    }
  }

  private FilesCollector initFilesCollector(boolean verbose) {
    final FilesCollectionProcessor processor = (System.getProperty(TEST_LOG) == null) ?
      new FilesCollectionProcessor(myLogger, myLockedFileResolver, verbose, myLockingProcessesKill) :
      new FilesCollectionProcessorForTests(myLogger, myLockedFileResolver, verbose, true, System.getProperty(TEST_LOG));

    return new FilesCollector(processor, myLogger);
  }

  private void makeSnapshot() {
    final String snapshotName = myPropertiesProcessor.getSnapshotName(myCheckoutDir);
    if (!new SnapshotGenerator(myCheckoutDir, myTempDir, myLogger).generateSnapshot(snapshotName)) {
      myCleanupEnabled = false;
    } else {
      myPropertiesProcessor.setSnapshot(myCheckoutDir,
        myStrict ? snapshotName : myPropertiesProcessor.markSnapshotName(snapshotName));
    }
  }

  private void logSettings(boolean enabled, File checkoutDir,
                           boolean lockingProcessesKill, boolean strict,
                           boolean lockingProcessesReport,
                           boolean verbose) {
    myLogger.debug("Swabra settings: enabled = '" + enabled +
      "', checkoutDir = " + checkoutDir.getAbsolutePath() +
      "', strict = " + strict +
      "', kill = " + lockingProcessesKill +
      "', locking processes report = " + lockingProcessesReport +
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

  private void fail() {
    myCleanupEnabled = false;
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