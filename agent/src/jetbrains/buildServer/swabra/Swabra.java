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

import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.swabra.processes.HandlePidsProvider;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.FilesCollectionProcessor;
import jetbrains.buildServer.swabra.snapshots.FilesCollectionProcessorForTests;
import jetbrains.buildServer.swabra.snapshots.FilesCollector;
import jetbrains.buildServer.swabra.snapshots.SnapshotGenerator;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.io.File;


public final class Swabra extends AgentLifeCycleAdapter {
  public static final String CACHE_KEY = "swabra";
  public static final String TEST_LOG = "swabra.test.log";

  @NotNull
  private final SmartDirectoryCleaner myDirectoryCleaner;
  private SwabraLogger myLogger;
  @NotNull
  private SwabraPropertiesProcessor myPropertiesProcessor;
//  @NotNull
//  private ProcessTerminator myProcessTerminator;

  private LockedFileResolver myLockedFileResolver;
  private SwabraSettings mySettings;

  private File myCheckoutDir;

  public Swabra(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher,
                @NotNull final SmartDirectoryCleaner directoryCleaner,
                @NotNull final SwabraLogger logger,
                @NotNull final SwabraPropertiesProcessor propertiesProcessor
                /*,@NotNull ProcessTerminator processTerminator*/) {
    agentDispatcher.addListener(this);
    myDirectoryCleaner = directoryCleaner;
    myLogger = logger;
    myPropertiesProcessor = propertiesProcessor;
//    myProcessTerminator = processTerminator;
  }

  @Override
  public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
    myLogger.setBuildLogger(runningBuild.getBuildLogger());

    myCheckoutDir = runningBuild.getCheckoutDirectory();

    mySettings = new SwabraSettings(runningBuild.getRunnerParameters(), myLogger);

    myLockedFileResolver = mySettings.isLockingProcessesDetectionEnabled() ?
      new LockedFileResolver(new HandlePidsProvider(mySettings.getHandlePath())/*, myProcessTerminator,*/) : null;

    String snapshotName;
    try {
      if (!mySettings.isCleanupEnabled()) {
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
      if (mySettings.isStrict()) {
        myLogger.swabraDebug("Snapshot " + snapshotName + " was saved without \"Ensure clean checkout\" mode. Will force clean checkout");
        doCleanup(myCheckoutDir);
        return;
      }
    }

    final FilesCollector filesCollector = initFilesCollector(mySettings.isVerbose());
    final FilesCollector.CollectionResult result = filesCollector.collect(myPropertiesProcessor.getSnapshotFile(snapshotName), myCheckoutDir);

    switch (result) {
      case ERROR:
        myLogger.swabraDebug("Some error occurred during files collecting. Will force clean checkout");
        doCleanup(myCheckoutDir);
        return;

      case DIRTY:
        if (mySettings.isStrict()) {
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
    if (!mySettings.isCleanupEnabled()) return;
    makeSnapshot();
  }

  @Override
  public void beforeRunnerStart(@NotNull final AgentRunningBuild runningBuild) {
    if (!mySettings.isCleanupEnabled()) return;
    if (!runningBuild.isCheckoutOnAgent() && !runningBuild.isCheckoutOnServer()) {
      makeSnapshot();
    }
  }

  @Override
  public void beforeBuildFinish(@NotNull final BuildFinishedStatus buildStatus) {
    if (!mySettings.isLockingProcessesDetectionEnabled()) return;

    myLogger.activityStarted();
    try {
      myLockedFileResolver.resolve(myCheckoutDir, mySettings.isLockingProcessesKill(), new LockedFileResolver.Listener() {
        public void message(String m) {
          myLogger.message(m, true);
        }

        public void warning(String w) {
          myLogger.warn(w);
        }
      });
    } finally {
      myLogger.activityFinished();
    }
  }

  private FilesCollector initFilesCollector(boolean verbose) {
    final FilesCollectionProcessor processor = (System.getProperty(TEST_LOG) == null) ?
      new FilesCollectionProcessor(myLogger, myLockedFileResolver, verbose, mySettings.isLockingProcessesKill()) :
      new FilesCollectionProcessorForTests(myLogger, myLockedFileResolver, verbose, true, System.getProperty(TEST_LOG));

    return new FilesCollector(processor, myLogger);
  }

  private void makeSnapshot() {
    final String snapshotName = myPropertiesProcessor.getSnapshotName(myCheckoutDir);
    if (!new SnapshotGenerator(myCheckoutDir, myLogger).generateSnapshot(myPropertiesProcessor.getSnapshotFile(snapshotName))) {
      mySettings.setCleanupEnabled(false);
    } else {
      myPropertiesProcessor.setSnapshot(myCheckoutDir,
        mySettings.isStrict() ? snapshotName : myPropertiesProcessor.markSnapshotName(snapshotName));
    }
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
    mySettings.setCleanupEnabled(false);
    myLogger.message("##teamcity[buildStatus status='FAILURE' text='Swabra failed cleanup: some files are locked']", true);
  }
}