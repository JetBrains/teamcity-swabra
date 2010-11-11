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
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.messages.serviceMessages.BuildStatus;
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

    final SwabraPropertiesProcessor.DirectoryState directoryState;
    try {
      if (!mySettings.isCleanupEnabled()) {
        myLogger.message("Swabra cleanup is disabled", false);
        return;
      }

      if (runningBuild.isCleanBuild() || !mySettings.getCheckoutDir().isDirectory()) {
        myLogger.swabraDebug("Clean build. No need to cleanup");
        return;
      }

      directoryState = myPropertiesProcessor.getState(mySettings.getCheckoutDir());
      myLogger.swabraDebug("Checkout directory state is " + directoryState);
    } finally {
      myPropertiesProcessor.deleteRecord(mySettings.getCheckoutDir());
    }

    switch (directoryState) {
      case CLEAN:
        // do nothing
        myLogger.swabraDebug("Checkout directory is clean");
        return;
      case DIRTY:
        if (mySettings.isStrict()) {
          myLogger.swabraDebug("Checkout directory is dirty");
          doCleanup(mySettings.getCheckoutDir());
          return;
        }
        if (!mySettings.isCleanupBeforeBuild()) {
          return;
        }
        // else fall into next case
      case PENDING:
        if (mySettings.isStrict()) {
          doCleanup(mySettings.getCheckoutDir());
          return;
        }
        // else fall into next case
      case STRICT_PENDING:
        myLogger.swabraDebug("Cleanup is performed before build");
        final FilesCollector filesCollector = initFilesCollector();
        filesCollector.collect(myPropertiesProcessor.getSnapshotFile(mySettings.getCheckoutDir()), mySettings.getCheckoutDir(),
          mySettings.isStrict() ?
          new FilesCollector.CollectionResultHandler() {
            public void success() {
              myLogger.message("Successfully performed cleanup", false);
            }

            public void error() {
              myLogger.message("Some error occurred during files collecting. Will force clean checkout", false);
              doCleanup(mySettings.getCheckoutDir());
            }

            public void lockedFilesDetected() {
              fail();
            }

            public void dirtyStateDetected() {
              myLogger.message("Checkout directory contains modified files or some files were deleted. Will force clean checkout", false);
              doCleanup(mySettings.getCheckoutDir());
            }
          }
          : null
        );
        return;
      case UNKNOWN:
      default:
        myLogger.message("Checkout directory state is unknown. Will force clean checkout", false);
        doCleanup(mySettings.getCheckoutDir());
    }
  }

  @Override
  public void sourcesUpdated(@NotNull AgentRunningBuild runningBuild) {
    if (!mySettings.isCleanupEnabled()) return;
    makeSnapshot();
  }

  @Override
  public void beforeRunnerStart(@NotNull BuildRunnerContext runner) {
    if (!mySettings.isCleanupEnabled()) return;
    final AgentRunningBuild runningBuild = runner.getBuild();
    if (!runningBuild.isCheckoutOnAgent() && !runningBuild.isCheckoutOnServer()) {
      makeSnapshot();
    }
  }

  @Override
  public void beforeBuildFinish(@NotNull AgentRunningBuild build, @NotNull BuildFinishedStatus buildStatus) {
    if (mySettings.isCleanupAfterBuild()) {
      myLogger.message("Build files cleanup will be performed after the build. Please refer to teamcity-agent.log for details", true);
    }

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

  @Override
  public void buildFinished(@NotNull AgentRunningBuild build, @NotNull BuildFinishedStatus buildStatus) {
    if (!mySettings.isCleanupAfterBuild()) return;

    myLogger.swabraDebug("Cleanup is performed after build");

    myPropertiesProcessor.deleteRecord(mySettings.getCheckoutDir());

    final FilesCollector filesCollector = initFilesCollector();
    filesCollector.collect(myPropertiesProcessor.getSnapshotFile(mySettings.getCheckoutDir()), mySettings.getCheckoutDir(),
      new FilesCollector.CollectionResultHandler() {
        public void success() {
          if (mySettings.isStrict()) {
            myPropertiesProcessor.markClean(mySettings.getCheckoutDir());
          } else {
            myPropertiesProcessor.markDirty(mySettings.getCheckoutDir());
          }
        }

        public void error() {
          myPropertiesProcessor.markDirty(mySettings.getCheckoutDir());
        }

        public void lockedFilesDetected() {
          myPropertiesProcessor.markPending(mySettings.getCheckoutDir(), mySettings.isStrict());
        }

        public void dirtyStateDetected() {
          myPropertiesProcessor.markDirty(mySettings.getCheckoutDir());
        }
      }
    );
  }

  private FilesCollector initFilesCollector() {
    FilesCollectionProcessor processor;
    if (System.getProperty(TEST_LOG) != null) {
      processor = new FilesCollectionProcessorMock(myLogger, myLockedFileResolver, mySettings.isVerbose(), mySettings.isStrict(), System.getProperty(TEST_LOG));
    } else if (mySettings.getRules().isEmpty()) {
      processor = new FilesCollectionProcessor(myLogger, myLockedFileResolver, mySettings.isVerbose(), mySettings.isStrict());
    } else {
      processor = new FilesCollectionRulesAwareProcessor(myLogger, myLockedFileResolver, mySettings);
    }
    return new FilesCollector(processor, myLogger);
  }

  private void makeSnapshot() {
    if (!new SnapshotGenerator(mySettings.getCheckoutDir(), myLogger).generateSnapshot(myPropertiesProcessor.getSnapshotFile(mySettings.getCheckoutDir()))) {
      mySettings.setCleanupEnabled(false);
    } else {
      myPropertiesProcessor.markPending(mySettings.getCheckoutDir(), mySettings.isStrict());
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
        fail();
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
    myLogger.activityFinished();
  }

  private void fail() {
    mySettings.setCleanupEnabled(false);
    final String message = "Swabra cleanup failed";
    myLogger.error(message + ": some files are locked");
    myLogger.message(new BuildStatus("{build.status.text}; " + message, Status.FAILURE).asString(), true);
  }
}