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
import jetbrains.buildServer.swabra.snapshots.*;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.io.File;


public final class Swabra extends AgentLifeCycleAdapter {
  public static final String DEBUG_MODE = "swabra.debug.mode";

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
//    System.setProperty(DEBUG_MODE, "true");

    myLogger.setBuildLogger(runningBuild.getBuildLogger());

    mySettings = new SwabraSettings(runningBuild, myLogger);

    myLockedFileResolver = mySettings.isLockingProcessesDetectionEnabled() ?
      new LockedFileResolver(new HandlePidsProvider(mySettings.getHandlePath())/*, myProcessTerminator,*/) : null;

    String snapshotName;
    try {
      if (!mySettings.isCleanupEnabled()) {
        myLogger.message("Swabra cleanup is disabled", false);
        return;
      }

      if (runningBuild.isCleanBuild() || !mySettings.getCheckoutDir().isDirectory()) {
        myLogger.swabraDebug("Clean build. No need to cleanup");
        return;
      }

      snapshotName = myPropertiesProcessor.getSnapshot(mySettings.getCheckoutDir());
    } finally {
      myPropertiesProcessor.deleteRecord(mySettings.getCheckoutDir());
    }
    if (snapshotName == null) {
      myLogger.swabraDebug("No snapshot saved in " + myPropertiesProcessor.getPropertiesFile().getAbsolutePath() + " for this configuration. Will force clean checkout");
      doCleanup(mySettings.getCheckoutDir());
      return;
    }
    if (myPropertiesProcessor.isMarkedSnapshot(snapshotName) && mySettings.isStrict()) {
      myLogger.swabraDebug("Snapshot " + snapshotName + " was saved without \"Ensure clean checkout\" mode. Will force clean checkout");
      doCleanup(mySettings.getCheckoutDir());
      return;
    }

    final FilesCollector filesCollector = initFilesCollector();
    final FilesCollector.CollectionResult result = filesCollector.collect(myPropertiesProcessor.getSnapshotFile(snapshotName), mySettings.getCheckoutDir());

    switch (result) {
      case ERROR:
        myLogger.swabraDebug("Some error occurred during files collecting. Will force clean checkout");
        doCleanup(mySettings.getCheckoutDir());
        return;

      case DIRTY:
        if (mySettings.isStrict()) {
          myLogger.swabraDebug("Checkout directory contains modified files or some files were deleted. Will force clean checkout");
          doCleanup(mySettings.getCheckoutDir());
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
      myLockedFileResolver.resolve(mySettings.getCheckoutDir(), mySettings.isLockingProcessesKill(), new LockedFileResolver.Listener() {
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

  private FilesCollector initFilesCollector() {
    FilesCollectionProcessor processor;
    if (System.getProperty(TEST_LOG) != null) {
      processor = new FilesCollectionProcessorForTests(myLogger, myLockedFileResolver, mySettings.isVerbose(), mySettings.isStrict(), System.getProperty(TEST_LOG));
    } else if (mySettings.getIgnoredPaths().isEmpty()) {
      processor = new FilesCollectionProcessor(myLogger, myLockedFileResolver, mySettings.isVerbose(), mySettings.isStrict());
    } else {
      processor = new FilesCollectionIgnoreRulesProcessor(myLogger, myLockedFileResolver, mySettings);
    }
    return new FilesCollector(processor, myLogger);
  }

  private void makeSnapshot() {
    final String snapshotName = myPropertiesProcessor.getSnapshotName(mySettings.getCheckoutDir());
    if (!new SnapshotGenerator(mySettings.getCheckoutDir(), myLogger).generateSnapshot(myPropertiesProcessor.getSnapshotFile(snapshotName))) {
      mySettings.setCleanupEnabled(false);
    } else {
      myPropertiesProcessor.setSnapshot(mySettings.getCheckoutDir(),
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
    final String message = "Swabra cleanup failed";
    myLogger.error(message + ": some files are locked");
    myLogger.message("##teamcity[buildStatus status='FAILURE' text='{build.status.text}; " + message + "']", true);
  }
}