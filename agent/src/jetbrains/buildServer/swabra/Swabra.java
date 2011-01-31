/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import jetbrains.buildServer.swabra.processes.HandleProcessesProvider;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.*;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


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

  private boolean mySnapshotSaved;
  private boolean myFailureReported;

  private Map<File, Thread> myPrevThreads = new HashMap<File, Thread>();

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
    mySnapshotSaved = false;
    myFailureReported = false;

    mySettings = new SwabraSettings(runningBuild);

    // setBuildLogger will be invoked later to avoid messages from prev build appearing in current build log
    SwabraLogger.activityStarted(runningBuild.getBuildLogger());

    try {
      waitForUnfinishedThreads(mySettings.getCheckoutDir(), runningBuild.getBuildLogger());

      myLogger.setBuildLogger(runningBuild.getBuildLogger());

      mySettings.prepareHandle(myLogger);

      myLockedFileResolver = mySettings.isLockingProcessesDetectionEnabled() ?
        new LockedFileResolver(new HandleProcessesProvider(mySettings.getHandlePath())/*, myProcessTerminator,*/) : null;

      final SwabraPropertiesProcessor.DirectoryState directoryState;
      try {
        if (!mySettings.isCleanupEnabled()) {
          myLogger.message("Swabra cleanup is disabled", false);
          return;
        }

        if (runningBuild.isCleanBuild() || !mySettings.getCheckoutDir().isDirectory()) {
          myLogger.message("Clean build. No need to cleanup", false);
          return;
        }

        directoryState = myPropertiesProcessor.getState(mySettings.getCheckoutDir());
        myLogger.message("Checkout directory state is " + directoryState, false);
      } finally {
        myPropertiesProcessor.deleteRecord(mySettings.getCheckoutDir());
      }

      switch (directoryState) {
        case STRICT_CLEAN:
          // do nothing
          myLogger.debug("Checkout directory is clean");
          return;
        case CLEAN:
          if (mySettings.isStrict()) {
            doCleanup(mySettings.getCheckoutDir(), "Checkout directory may contain newly created, modified or deleted files", runningBuild);
          }
          return;
        case DIRTY:
          if (mySettings.isStrict()) {
            doCleanup(mySettings.getCheckoutDir(), "Checkout directory contains newly created, modified or deleted files", runningBuild);
            return;
          }
          if (!mySettings.isCleanupBeforeBuild()) {
            return;
          }
          // else fall into next case
        case PENDING:
          if (mySettings.isStrict()) {
            doCleanup(mySettings.getCheckoutDir(),
              "Checkout directory snapshot may contain information about newly created, modified or deleted files", runningBuild);
            return;
          }
          // else fall into next case
        case STRICT_PENDING:
          myLogger.debug("Cleanup is performed before build");
          final FilesCollector filesCollector = initFilesCollector();
          filesCollector.collect(myPropertiesProcessor.getSnapshotFile(mySettings.getCheckoutDir()), mySettings.getCheckoutDir(),
            mySettings.isStrict() ?
              new FilesCollector.CollectionResultHandler() {
                public void success() {
                  myLogger.message("Successfully performed cleanup", false);
                }

                public void error() {
                  doCleanup(mySettings.getCheckoutDir(), "Some error occurred during cleanup", runningBuild);
                }

                public void lockedFilesDetected() {
                  fail();
                }

                public void dirtyStateDetected() {
                  doCleanup(mySettings.getCheckoutDir(),
                    "Checkout directory contains modified files or some files were deleted", runningBuild);
                }
              }
              : null
          );
          return;
        case UNKNOWN:
        default:
          doCleanup(mySettings.getCheckoutDir(), "Checkout directory state is unknown", runningBuild);
      }
    } finally {
      myLogger.activityFinished();
    }
  }

  private void waitForUnfinishedThreads(@NotNull File checkoutDir, @NotNull BuildProgressLogger logger) {
    final Thread t = myPrevThreads.get(checkoutDir);
    if ((t != null) && t.isAlive()) {
      logger.message("Waiting for build files cleanup started at previous build finish");
      try {
        t.join();
      } catch (InterruptedException e) {
        logger.message("Thread interrupted");
      }
    }
    myPrevThreads.remove(checkoutDir);
  }

  @Override
  public void sourcesUpdated(@NotNull AgentRunningBuild runningBuild) {
    makeSnapshot();
  }

  @Override
  public void beforeRunnerStart(@NotNull BuildRunnerContext runner) {
    makeSnapshot();
  }

  @Override
  public void beforeBuildFinish(@NotNull AgentRunningBuild build, @NotNull BuildFinishedStatus buildStatus) {
    myLogger.activityStarted();
    try {
      if (mySettings.isCleanupAfterBuild()) {
        myLogger.message("Cleanup will be performed after the build. Please refer to teamcity-agent.log for details", true);
      }

      if (!mySettings.isLockingProcessesDetectionEnabled()) return;

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

    myLogger.debug("Cleanup is performed after build");

    myPropertiesProcessor.deleteRecord(mySettings.getCheckoutDir());

    final FilesCollector filesCollector = initFilesCollector();

    myLogger.setBuildLogger(null);

    final Thread t = new Thread(new Runnable() {
      public void run() {
        filesCollector.collect(myPropertiesProcessor.getSnapshotFile(mySettings.getCheckoutDir()), mySettings.getCheckoutDir(),
          new FilesCollector.CollectionResultHandler() {
            public void success() {
              myPropertiesProcessor.markClean(mySettings.getCheckoutDir(), mySettings.isStrict());
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
    });
    myPrevThreads.put(mySettings.getCheckoutDir(), t);
    t.start();
  }

  private FilesCollector initFilesCollector() {
    FilesCollectionProcessor processor;
    if (System.getProperty(TEST_LOG) != null) {
      processor = new FilesCollectionProcessorMock(myLogger, myLockedFileResolver, mySettings.getCheckoutDir(), mySettings.isVerbose(), mySettings.isStrict(), System.getProperty(TEST_LOG));
    } else if (mySettings.getRules().isEmpty()) {
      processor = new FilesCollectionProcessor(myLogger, myLockedFileResolver, mySettings.getCheckoutDir(), mySettings.isVerbose(), mySettings.isStrict());
    } else {
      processor = new FilesCollectionRulesAwareProcessor(myLogger, myLockedFileResolver, mySettings);
    }
    return new FilesCollector(processor, myLogger, mySettings);
  }

  private void makeSnapshot() {
    if (!mySettings.isCleanupEnabled()) return;
    if (mySnapshotSaved) return;

    mySnapshotSaved = true;

    myLogger.activityStarted();
    try {
      if (!new SnapshotGenerator(mySettings.getCheckoutDir(), myLogger).generateSnapshot(myPropertiesProcessor.getSnapshotFile(mySettings.getCheckoutDir()))) {
        mySettings.setCleanupEnabled(false);
      } else {
        myPropertiesProcessor.markPending(mySettings.getCheckoutDir(), mySettings.isStrict());
      }
    } finally {
      myLogger.activityFinished();
    }
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
        ((AgentRunningBuildEx) build).stopBuild("Swabra cleanup failed: some files are locked");
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

    final String message = "Swabra cleanup failed";
    myLogger.error(message + ": some files are locked");
    myLogger.message(new BuildStatus("{build.status.text}; " + message, Status.FAILURE).asString(), true);
  }
}
