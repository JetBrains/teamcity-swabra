/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.io.File;
import java.util.Collection;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.swabra.processes.HandleProcessesProvider;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.*;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class Swabra extends AgentLifeCycleAdapter {
  public static final String DEBUG_MODE = "swabra.debug.mode";

  public static final String CACHE_KEY = "swabra";
  public static final String TEST_LOG = "swabra.test.log";

  @NotNull
  private final SmartDirectoryCleaner myDirectoryCleaner;
  private final SwabraLogger myLogger;
  @NotNull
  private final SwabraPropertiesProcessor myPropertiesProcessor;
  @NotNull
  private final BundledToolsRegistry myToolsRegistry;
//  @NotNull
//  private ProcessTerminator myProcessTerminator;

  private LockedFileResolver myLockedFileResolver;
  private SwabraSettings mySettings;

  private boolean mySnapshotSaved;
  private boolean myFailureReported;

  private AgentRunningBuild myRunningBuild;

  public Swabra(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher,
                @NotNull final SmartDirectoryCleaner directoryCleaner,
                @NotNull final SwabraLogger logger,
                @NotNull final SwabraPropertiesProcessor propertiesProcessor,
                @NotNull final BundledToolsRegistry toolsRegistry
                /*,@NotNull ProcessTerminator processTerminator*/) {
    agentDispatcher.addListener(this);
    myDirectoryCleaner = directoryCleaner;
    myLogger = logger;
    myPropertiesProcessor = propertiesProcessor;
    myToolsRegistry = toolsRegistry;
//    myProcessTerminator = processTerminator;
  }

  @Override
  public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
//    System.setProperty(DEBUG_MODE, "true");
    myRunningBuild = runningBuild;
    mySnapshotSaved = false;
    myFailureReported = false;

    mySettings = new SwabraSettings(runningBuild);
    myLogger.setBuildLogger(runningBuild.getBuildLogger());

    myLogger.activityStarted();
    try {
      mySettings.prepareHandle(myLogger, myToolsRegistry);

      myLockedFileResolver = mySettings.isLockingProcessesDetectionEnabled() ?
        new LockedFileResolver(new HandleProcessesProvider(mySettings.getHandlePath())/*, myProcessTerminator,*/) : null;

      if (!mySettings.isCleanupEnabled()) {
        myLogger.message("Swabra cleanup is disabled", false);
        myPropertiesProcessor.deleteRecords(mySettings.getRules().getPaths());
        return;
      }

      processDirs(mySettings.getRules().getPaths());

    } finally {
      myLogger.activityFinished();
    }
  }

  @Override
  public void sourcesUpdated(@NotNull AgentRunningBuild runningBuild) {
    makeSnapshots(mySettings.getRules().getPaths());
  }

  @Override
  public void beforeRunnerStart(@NotNull BuildRunnerContext runner) {
    makeSnapshots(mySettings.getRules().getPaths());
  }

  @Override
  public void beforeBuildFinish(@NotNull AgentRunningBuild build, @NotNull BuildFinishedStatus buildStatus) {
    if (!mySettings.isLockingProcessesDetectionEnabled()) return;

    myLogger.activityStarted();
    try {
      for (File dir : mySettings.getRules().getPaths()) {
        myLockedFileResolver.resolve(dir, mySettings.isLockingProcessesKill(), new LockedFileResolver.Listener() {
          public void message(String m) {
            myLogger.message(m, true);
          }

          public void warning(String w) {
            myLogger.warn(w);
          }
        });
      }
    } finally {
      myLogger.activityFinished();
    }
  }

  @Override
  public void afterAtrifactsPublished(@NotNull final AgentRunningBuild runningBuild, @NotNull final BuildFinishedStatus status) {
    if (!mySettings.isCleanupAfterBuild()) return;

    myLogger.debug("Cleanup is performed after build");

    myLogger.activityStarted();
    try {
      collectFiles(mySettings.getRules().getPaths());
    } finally {
      myLogger.activityFinished();
    }
  }

  @Override
  public void buildFinished(@NotNull final AgentRunningBuild build, @NotNull final BuildFinishedStatus buildStatus) {
    myRunningBuild = null;
  }

  private void processDirs(@NotNull Collection<File> dirs) {
    for (File dir : dirs) {
      processDir(dir);
    }
  }

  private void processDir(@NotNull File dir) {
    if (dir.equals(mySettings.getCheckoutDir())) {
      processCheckoutDir(dir);
    } else {
      processNonCheckoutDir(dir);
    }
  }

  private void processCheckoutDir(@NotNull final File checkoutDir) {
    final SwabraPropertiesProcessor.DirectoryState directoryState = getAndCleanDirectoryState(checkoutDir);

    if (myRunningBuild.isCleanBuild() || !checkoutDir.isDirectory()) {
      myLogger.message("Clean build. No need to clean up in checkout directory ", false);
      return;
    }

    switch (directoryState) {
      case STRICT_CLEAN:
        // do nothing
        return;
      case CLEAN:
        if (mySettings.isStrict()) {
          doCleanup(checkoutDir, "Checkout directory may contain newly created, modified or deleted files", myRunningBuild);
        }
        return;
      case DIRTY:
        if (mySettings.isStrict()) {
          doCleanup(checkoutDir, "Checkout directory contains newly created, modified or deleted files", myRunningBuild);
        } else if (mySettings.isCleanupBeforeBuild()) {
          myLogger.debug("Checkout directory cleanup is performed before build");
          collectFilesInCheckoutDir(checkoutDir);
        }
        return;
      case PENDING:
        if (mySettings.isStrict()) {
          doCleanup(checkoutDir, "Checkout directory snapshot may contain information about newly created, modified or deleted files",
                    myRunningBuild);
        } else{
          myLogger.debug("Checkout directory cleanup is performed before build");
          collectFilesInCheckoutDir(checkoutDir);
        }
        return;
      case STRICT_PENDING:
        myLogger.debug("Checkout directory cleanup is performed before build");
        collectFilesInCheckoutDir(checkoutDir);
        return;
      case UNKNOWN:
      default:
        doCleanup(checkoutDir, "Checkout directory state is unknown", myRunningBuild);
    }
  }

  private void collectFilesInCheckoutDir(@NotNull final File checkoutDir) {
    collectFiles(checkoutDir,
                 mySettings.isStrict() ?
                 new FilesCollector.CollectionResultHandler() {
                   public void success() {
                     myLogger.message("Successfully performed checkout directory cleanup", false);
                   }

                   public void error() {
                     doCleanup(checkoutDir, "Some error occurred during checkout directory cleanup", myRunningBuild);
                   }

                   public void lockedFilesDetected() {
                     fail();
                   }

                   public void dirtyStateDetected() {
                     doCleanup(checkoutDir,
                               "Checkout directory contains modified files or some files were deleted", myRunningBuild);
                   }
                 }
                                       : null
    );
  }

  private void processNonCheckoutDir(@NotNull File dir) {
    final SwabraPropertiesProcessor.DirectoryState directoryState = getAndCleanDirectoryState(dir);

    switch (directoryState) {
      case STRICT_CLEAN:
      case CLEAN:
        // do nothing
        return;
      case DIRTY:
        if (mySettings.isCleanupBeforeBuild()) {
          myLogger.debug(dir + " cleanup is performed before build");
          collectFiles(dir, null);
        }
        return;
      case PENDING:
      case STRICT_PENDING:
        myLogger.debug(dir + " cleanup is performed before build");
        collectFiles(dir, null);
        return;
      case UNKNOWN:
      default:
        myLogger.debug(dir + " directory state is unknown");
    }
  }

  private SwabraPropertiesProcessor.DirectoryState getAndCleanDirectoryState(@NotNull File dir) {
    final SwabraPropertiesProcessor.DirectoryState directoryState = myPropertiesProcessor.getState(dir);
    myLogger.message(dir + " directory state is " + directoryState, false);
    myPropertiesProcessor.deleteRecord(dir);
    return directoryState;
  }

  private void collectFiles(@NotNull File dir, @Nullable FilesCollector.CollectionResultHandler handler) {
    final FilesCollector filesCollector = initFilesCollector(dir);
    filesCollector.collect(myPropertiesProcessor.getSnapshotFile(dir), dir, handler);
  }

  private FilesCollector initFilesCollector(@NotNull File dir) {
    FilesCollectionProcessor processor;
    if (System.getProperty(TEST_LOG) != null) {
      processor = new FilesCollectionProcessorMock(myLogger, myLockedFileResolver, dir, mySettings.isVerbose(), mySettings.isStrict(), System.getProperty(TEST_LOG));
    } else if (mySettings.getRules().getRulesForPath(dir).size() == 1) {
      processor = new FilesCollectionProcessor(myLogger, myLockedFileResolver, dir, mySettings.isVerbose(), mySettings.isStrict());
    } else {
      processor = new FilesCollectionRulesAwareProcessor(myLogger, myLockedFileResolver, dir, mySettings);
    }
    return new FilesCollector(processor, myLogger, mySettings);
  }

  private void makeSnapshots(@NotNull Collection<File> dirs) {
    if (!mySettings.isCleanupEnabled()) return;
    if (mySnapshotSaved) return;

    mySnapshotSaved = true;

    myLogger.activityStarted();
    try {
      for (File dir : dirs) {
        makeSnapshot(dir);
      }
    } finally {
      myLogger.activityFinished();
    }
  }

  private void makeSnapshot(@NotNull File dir) {
    if (!new SnapshotGenerator(dir, myLogger).generateSnapshot(myPropertiesProcessor.getSnapshotFile(dir))) {
      mySettings.setCleanupEnabled(false);
    } else {
      myPropertiesProcessor.markPending(dir, mySettings.isStrict());
    }
  }

  private void collectFiles(@NotNull Collection<File> dirs) {
   for (File dir : dirs) {
     collectFiles(dir);
   }
  }

  private void collectFiles(@NotNull final File dir) {
    myPropertiesProcessor.deleteRecord(dir);

    collectFiles(dir,
                 new FilesCollector.CollectionResultHandler() {
                   public void success() {
                     myPropertiesProcessor.markClean(dir, mySettings.isStrict());
                   }

                   public void error() {
                     myPropertiesProcessor.markDirty(dir);
                   }

                   public void lockedFilesDetected() {
                     myPropertiesProcessor.markPending(dir, mySettings.isStrict());
                   }

                   public void dirtyStateDetected() {
                     myPropertiesProcessor.markDirty(dir);
                   }
                 }
    );
  }

  private void doCleanup(@NotNull File checkoutDir, @Nullable final String reason, @NotNull final AgentRunningBuild build) {
    myDirectoryCleaner.cleanFolder(checkoutDir, new SmartDirectoryCleanerCallback() {
      public void logCleanStarted(File dir) {
        String message = (reason == null ? "" : reason + ". ")
          + "Need a clean checkout directory snapshot - forcing clean checkout";
        myLogger.message(message, true);
      }

      public void logFailedToDeleteEmptyDirectory(File dir) {
        myLogger.error("Failed to delete empty directory " + dir.getAbsolutePath());
        fail();
        build.stopBuild("Swabra cleanup failed: some files are locked");
      }

      public void logFailedToCleanFilesUnderDirectory(File dir) {
        myLogger.error("Failed to delete files in directory " + dir.getAbsolutePath());
        fail();
      }

      public void logFailedToCleanFile(File file) {
        myLogger.error("Failed to delete file " + file.getAbsolutePath());
        fail();
      }

      public void logFailedToCleanEntireFolder(File dir) {
        myLogger.error("Failed to delete directory " + dir.getAbsolutePath());
        fail();
      }
    });
  }

  private void fail() {
    if (myFailureReported) return;

    myFailureReported = true;
    mySettings.setCleanupEnabled(false);
    myLogger.failBuild();
  }
}
