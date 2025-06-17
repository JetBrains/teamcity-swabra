

package jetbrains.buildServer.swabra;

/**
 * User: vbedrosova
 * Date: 14.04.2009
 * Time: 14:10:58
 */

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.TeamCityRuntimeException;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.impl.directories.DirectoryMapDirectoriesCleaner;
import jetbrains.buildServer.agent.impl.operationModes.AgentOperationModeHolder;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.processes.WmicProcessDetailsProvider;
import jetbrains.buildServer.swabra.snapshots.*;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.positioning.PositionAware;
import jetbrains.buildServer.windows.LockingProcessesFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.agent.AgentRuntimeProperties.FAIL_ON_CLEAN_CHECKOUT;


public final class Swabra extends AgentLifeCycleAdapter implements PositionAware {
  public static final String DEBUG_MODE = "swabra.debug.mode";

  public static final String CACHE_KEY = "swabra";
  public static final String TEST_LOG = "swabra.test.log";

  private final SwabraLogger myLogger;
  @NotNull
  private final SwabraPropertiesProcessor myPropertiesProcessor;
  @NotNull
  private final BundledToolsRegistry myToolsRegistry;
  @NotNull private final DirectoryMapDirectoriesCleaner myDirectoriesCleaner;
  @NotNull private final LockedFileResolver.LockingProcessesProviderFactory myLockingProcessesProviderFactory;

  private LockedFileResolver myLockedFileResolver;
  private SwabraSettings mySettings;

  private boolean mySnapshotSaved;
  private boolean myFailureReported;

  // this field is used in tests only
  private FilesCollectionProcessor myInternalProcessor;

  private AgentRunningBuild myRunningBuild;

  private final AgentOperationModeHolder myOperationModeHolder;

  private AtomicBoolean myBuildInterrupted = new AtomicBoolean(false);

  public Swabra(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher,
                @NotNull final SwabraLogger logger,
                @NotNull final SwabraPropertiesProcessor propertiesProcessor,
                @NotNull final BundledToolsRegistry toolsRegistry,
                @NotNull final DirectoryMapDirectoriesCleaner directoriesCleaner,
                @NotNull final AgentOperationModeHolder operationModeHolder) {
    this(agentDispatcher, logger, propertiesProcessor, toolsRegistry, directoriesCleaner, new LockedFileResolver.LockingProcessesProviderFactory() {

      @Nullable
      @Override
      public LockedFileResolver.LockingProcessesProvider createProvider(final SwabraSettings swabraSettings) {
        if (swabraSettings.getHandlePath() == null)
          return null;
        LockingProcessesFinder lockingProcessesFinder = new LockingProcessesFinder(swabraSettings.getHandlePath());
        return f -> lockingProcessesFinder.getLockingProcesses(f);
      }
    }, operationModeHolder);
  }

  @Used("tests")
  public Swabra(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher,
                @NotNull final SwabraLogger logger,
                @NotNull final SwabraPropertiesProcessor propertiesProcessor,
                @NotNull final BundledToolsRegistry toolsRegistry,
                @NotNull final DirectoryMapDirectoriesCleaner directoriesCleaner,
                @NotNull final LockedFileResolver.LockingProcessesProviderFactory lockingProcessesProviderFactory,
                @NotNull final AgentOperationModeHolder operationModeHolder) {
    myDirectoriesCleaner = directoriesCleaner;
    myOperationModeHolder = operationModeHolder;
    agentDispatcher.addListener(this);
    myLogger = logger;
    myPropertiesProcessor = propertiesProcessor;
    myToolsRegistry = toolsRegistry;
    myLockingProcessesProviderFactory = lockingProcessesProviderFactory;
  }

  @NotNull
  public String getOrderId() {
    return "swabra"; // may be referenced in other plugins
  }

  @Override
  public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
//    System.setProperty(DEBUG_MODE, "true");
    myRunningBuild = runningBuild;
    mySnapshotSaved = false;
    myFailureReported = false;
    if (myRunningBuild.getInterruptReason() == null){
      myBuildInterrupted.set(false);
    }
    myLogger.setBuildLogger(myRunningBuild.getBuildLogger());

    mySettings = new SwabraSettings(myRunningBuild, myOperationModeHolder);

    mySettings.prepareHandle(myLogger, myToolsRegistry);

    if (!mySettings.isSwabraEnabled()) {
      myLogger.message("Swabra cleanup is disabled", false);
      myPropertiesProcessor.deleteRecords(mySettings.getCheckoutDir());
      return;
    }

    myLogger.activityStarted();
    try {
      final LockedFileResolver.LockingProcessesProvider provider = myLockingProcessesProviderFactory.createProvider(mySettings);
      myLockedFileResolver = mySettings.isLockingProcessesDetectionEnabled() && provider != null ?
                             new LockedFileResolver(provider,
                                                    mySettings.getIgnoredProcesses(),
                                                    new WmicProcessDetailsProvider()/*, myProcessTerminator,*/) : null;

      if (mySettings.isCleanupEnabled()) {
        processDirs(mySettings.getRules().getPaths());
      }

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
    if (mySettings == null || !mySettings.isLockingProcessesDetectionEnabled()) return;

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
    if (mySettings == null || !mySettings.isCleanupAfterBuild()) return;

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

  @Override
  public void beforeBuildInterrupted(@NotNull final AgentRunningBuild runningBuild, @NotNull final BuildInterruptReason reason) {
    myBuildInterrupted.set(true);
  }

  // for test purposes only
  /*package local*/ void setInternalProcessor(FilesCollectionProcessor processor, AtomicBoolean buildInterruptedFlag){
    myInternalProcessor = processor;
    myBuildInterrupted = buildInterruptedFlag;
  }

  private void processDirs(@NotNull Collection<File> dirs) {
    for (File dir : dirs) {
      if (myBuildInterrupted.get()) {
        myLogger.message("Will skip " + dir + " because interrupted", true);
        continue;
      }
      processDir(dir);
    }
  }

  private void processDir(@NotNull File dir) {
    if (FileUtil.isAncestor(mySettings.getCheckoutDir(), dir, false)) {
      processCheckoutDir(dir);
    } else {
      processExternalDir(dir);
    }
  }

  private void processCheckoutDir(@NotNull final File dir) {
    final String previousBuildTypeId = myPropertiesProcessor.getPreviousBuildTypeId(dir);
    final SwabraPropertiesProcessor.DirectoryState directoryState = getAndCleanDirectoryState(dir);

    if (myRunningBuild.isCleanBuild() || !dir.isDirectory()) {
      myLogger.message("Clean build. No need to clean up in checkout directory ", false);
      return;
    }

    switch (directoryState) {
      case STRICT_CLEAN:
        // do nothing
        return;
      case CLEAN:
        if (mySettings.isStrict()) {
          reportCleanCheckoutDetected(previousBuildTypeId);
          cleanupCheckoutDir("Checkout directory may contain newly created, modified or deleted files", myRunningBuild);
        }
        return;
      case DIRTY:
        if (mySettings.isStrict()) {
          reportCleanCheckoutDetected(previousBuildTypeId);
          cleanupCheckoutDir("Checkout directory contains newly created, modified or deleted files", myRunningBuild);
        } else if (mySettings.isCleanupBeforeBuild()) {
          myLogger.debug("Checkout directory cleanup is performed before build");
          collectFilesInCheckoutDir(dir);
        } else {
          myLogger.warn("Checkout directory contains modified or deleted files.");
          myLogger.message("Clean checkout not enforced - \"Force clean checkout if cannot restore clean directory state\" is unchecked", true);
        }
        return;
      case PENDING:
        if (mySettings.isStrict()) {
          reportCleanCheckoutDetected(previousBuildTypeId);
          cleanupCheckoutDir("Checkout directory snapshot may contain information about newly created, modified or deleted files", myRunningBuild);
        } else{
          myLogger.debug("Checkout directory cleanup is performed before build");
          collectFilesInCheckoutDir(dir);
        }
        return;
      case STRICT_PENDING:
        myLogger.debug("Checkout directory cleanup is performed before build");
        collectFilesInCheckoutDir(dir);
        return;
      case UNKNOWN:
      default:
        reportCleanCheckoutDetected(previousBuildTypeId);
        cleanupCheckoutDir("Checkout directory state is unknown", myRunningBuild);
    }
  }

  private void reportCleanCheckoutDetected(@Nullable String causeBuildTypeId) {
    if (StringUtil.isNotEmpty(causeBuildTypeId)) {
      myRunningBuild.addSharedConfigParameter(SwabraUtil.CLEAN_CHECKOUT_CAUSE_BUILD_TYPE_ID, causeBuildTypeId);
    }
  }

  private void collectFilesInCheckoutDir(@NotNull final File dir) {
    collectFiles(dir,
                 mySettings.isStrict() ?
                 new FilesCollector.CollectionResultHandler() {
                   public void success() {
                     myLogger.message("Successfully performed checkout directory cleanup", false);
                   }

                   public void error() {
                     cleanupCheckoutDir("Some error occurred during checkout directory cleanup", myRunningBuild);
                   }

                   public void lockedFilesDetected() {
                     fail();
                   }

                   public void dirtyStateDetected() {
                     cleanupCheckoutDir("Checkout directory contains modified files or some files were deleted", myRunningBuild);
                   }

                   public void interrupted() {
                     myPropertiesProcessor.markPending(dir, dir, mySettings.isStrict(), myRunningBuild.getBuildTypeId());
                   }
                 }
                                       :
                 new FilesCollector.SimpleCollectionResultHandler() {
                   @Override
                   public void interrupted() {
                     myPropertiesProcessor.markPending(dir, dir, mySettings.isStrict(), myRunningBuild.getBuildTypeId());
                   }

                   @Override
                   public void dirtyStateDetected() {
                     myLogger.warn("Checkout directory contains modified or deleted files.");
                     myLogger.message("Clean checkout not enforced - \"Force clean checkout if cannot restore clean directory state\" is unchecked", true);
                   }
                 }
    );
  }

  private void processExternalDir(@NotNull final File dir) {
    final SwabraPropertiesProcessor.DirectoryState directoryState = getAndCleanDirectoryState(dir);

    switch (directoryState) {
      case STRICT_CLEAN:
      case CLEAN:
        // do nothing
        return;
      case DIRTY:
        if (mySettings.isCleanupBeforeBuild()) {
          myLogger.debug(dir + " cleanup is performed before build");
          break;
        } else return;
      case PENDING:
      case STRICT_PENDING:
        myLogger.debug(dir + " cleanup is performed before build");
        break;
      case UNKNOWN:
      default:
        myLogger.debug(dir + " directory state is unknown");
        return;
    }

    collectFiles(dir, new FilesCollector.SimpleCollectionResultHandler() {
      @Override
      public void interrupted() {
        myPropertiesProcessor.markPending(dir, mySettings.getCheckoutDir(), mySettings.isStrict(), myRunningBuild.getBuildTypeId());
      }
    });
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
      if (myInternalProcessor!= null)
        processor = myInternalProcessor;
      else
        processor = new FilesCollectionProcessorMock(myLogger, myLockedFileResolver, dir, mySettings.isVerbose(), mySettings.isStrict(), System.getProperty(TEST_LOG), myBuildInterrupted);
    } else if (mySettings.getRules().getRulesForPath(dir).size() == 1 && !mySettings.getRules().requiresListingForDir(dir)) {
      processor = new FilesCollectionProcessor(myLogger, myLockedFileResolver, dir, mySettings.isVerbose(), mySettings.isStrict(), myBuildInterrupted);
    } else {
      processor = new FilesCollectionRulesAwareProcessor(myLogger, myLockedFileResolver, dir, mySettings, myBuildInterrupted);
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
    if (!new SnapshotGenerator(dir, myLogger, mySettings.getRules()).generateSnapshot(myPropertiesProcessor.getSnapshotFile(dir))) {
      mySettings.setCleanupEnabled(false);
    } else {
      myPropertiesProcessor.markPending(dir, mySettings.getCheckoutDir(), mySettings.isStrict(), myRunningBuild.getBuildTypeId());
    }
  }

  private void collectFiles(@NotNull Collection<File> dirs) {
   for (File dir : dirs) {
     if (myBuildInterrupted.get()) {
       myLogger.message("Will skip " + dir + " because interrupted", true);
       continue;
     }
     collectFiles(dir);
   }
  }

  private void collectFiles(@NotNull final File dir) {
    myPropertiesProcessor.deleteRecord(dir);

    collectFiles(dir,
                 new FilesCollector.CollectionResultHandler() {
                   public void success() {
                     myPropertiesProcessor.markClean(dir, mySettings.getCheckoutDir(), mySettings.isStrict(), myRunningBuild.getBuildTypeId());
                   }

                   public void error() {
                     myPropertiesProcessor.markDirty(dir, mySettings.getCheckoutDir(), myRunningBuild.getBuildTypeId());
                   }

                   public void lockedFilesDetected() {
                     myPropertiesProcessor.markPending(dir, mySettings.getCheckoutDir(), mySettings.isStrict(), myRunningBuild.getBuildTypeId());
                   }

                   public void dirtyStateDetected() {
                     myPropertiesProcessor.markDirty(dir, mySettings.getCheckoutDir(), myRunningBuild.getBuildTypeId());
                   }

                   public void interrupted() {
                     myPropertiesProcessor.markPending(dir, mySettings.getCheckoutDir(), mySettings.isStrict(), myRunningBuild.getBuildTypeId());
                   }
                 }
    );
  }

  private void cleanupCheckoutDir(@NotNull final String reason, @NotNull final AgentRunningBuild build) {

    if (cleanupIsDisabled(reason, build)) {
      return;
    }

    String message = reason + ". Need a clean checkout directory snapshot - forcing clean checkout";
    myLogger.message(message, true);
    myDirectoriesCleaner.removeCheckoutDirectories(Arrays.asList(mySettings.getCheckoutDir()), true);
  }

  private void fail() {
    if (myFailureReported) return;

    myFailureReported = true;
    mySettings.setCleanupEnabled(false);
    myLogger.failBuild();
  }


  private boolean cleanupIsDisabled(final @NotNull String reason, final @NotNull AgentRunningBuild build) {
    final String failOnCleanCheckoutProperty = build.getSharedConfigParameters().get(FAIL_ON_CLEAN_CHECKOUT);
    final boolean shouldIgnoreCleanCheckout = "ignoreAndContinue".equals(failOnCleanCheckoutProperty);

    if (StringUtil.isTrue(failOnCleanCheckoutProperty)) {
      final String errorMessage = String.format(FAIL_ON_CLEAN_LOG_MESSAGE, reason);
      myLogger.error(errorMessage);
      throw new TeamCityRuntimeException("Clean checkout is requested by Swabra but is not allowed");
    }

    if (shouldIgnoreCleanCheckout) {
      myLogger.warn(String.format(IGNORE_CLEAN_CHECKOUT_MESSAGE, reason));
      return true;
    }

    return false;
  }

  private static final String FAIL_ON_CLEAN_LOG_MESSAGE = "The checkout directory should be cleaned by Swabra plugin, reason: '%s',\n" +
                                                          "but clean checkout was disabled by the option " + FAIL_ON_CLEAN_CHECKOUT + ".\n" +
                                                          "When checkout directory state is reviewed and corrected,\n" +
                                                          "run build on this build agent with configuration parameter " + FAIL_ON_CLEAN_CHECKOUT + "=ignoreAndContinue\n" +
                                                          "Alternatively, you can cleanup checkout directory or enforce clean checkout on this agent from TeamCity UI.";

  private static final String IGNORE_CLEAN_CHECKOUT_MESSAGE = "Clean checkout is initiated by Swabra plugin due to: '%s',\n" +
                                                              "but it was disabled with " +
                                                              FAIL_ON_CLEAN_CHECKOUT + "=ignoreAndContinue configuration parameter,\n" +
                                                              "skip clean checkout and continue";
}