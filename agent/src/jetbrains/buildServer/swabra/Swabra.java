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

import com.intellij.util.io.ZipUtil;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.swabra.processes.HandlePidsProvider;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.processes.ProcessExecutor;
import jetbrains.buildServer.swabra.snapshots.FilesCollectionProcessor;
import jetbrains.buildServer.swabra.snapshots.FilesCollectionProcessorForTests;
import jetbrains.buildServer.swabra.snapshots.FilesCollector;
import jetbrains.buildServer.swabra.snapshots.SnapshotGenerator;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static jetbrains.buildServer.swabra.SwabraUtil.*;


public final class Swabra extends AgentLifeCycleAdapter {
  public static final String CACHE_KEY = "swabra";

  public static final String SNAPSHOT_SUFFIX = ".snapshot";

  public static final String HANDLE_EXE = "handle.exe";
  public static final String HANDLE_EXE_SYSTEM_PROP = "swabra.handle.exe.path";
  public static final String DISABLE_DOWNLOAD_HANDLE = "swabra.handle.disable.download";
  public static final String HANDLE_URL = "http://download.sysinternals.com/Files/Handle.zip";

  public static final String TEST_LOG = "swabra.test.log";

  private SwabraLogger myLogger;
  private SmartDirectoryCleaner myDirectoryCleaner;

  private SwabraPropertiesProcessor myPropertiesProcessor;
  private FilesCollector myFilesCollector;

  private String myMode;

  private File myCheckoutDir;
  private File myTempDir;

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
    final boolean verbose = isVerbose(runnerParams);
    final boolean strict = isStrict(runnerParams);
    myTempDir = runningBuild.getAgentConfiguration().getCacheDirectory(CACHE_KEY);

    final boolean lockingProcessesDetectionEnabled = isLockingProcessesDetectionEnabled(runnerParams);
    if (lockingProcessesDetectionEnabled) {
//      myHandlePath = getHandlePath(runnerParams);
      myHandlePath = runningBuild.getAgentConfiguration().getCacheDirectory("handle").getAbsolutePath() + "/handle.exe";
      if (!prepareHandle()) {
        myHandlePath = null;
        myLogger.swabraMessage("No Handle executable prepared", false);
      }
    } else {
      myHandlePath = null;
    }

    logSettings(myMode, myCheckoutDir.getAbsolutePath(), strict, lockingProcessesDetectionEnabled, myHandlePath, verbose);

    myPropertiesProcessor = new SwabraPropertiesProcessor(myTempDir, myLogger);
    myPropertiesProcessor.readProperties();

    if (!isEnabled(myMode)) {
      myLogger.message("Swabra is disabled", false);
      myPropertiesProcessor.markDirty(myCheckoutDir);
      return;
    }

    myFilesCollector = initFilesCollector(buildLogger, verbose, strict);

    final File snapshot;
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
          myLogger.swabraDebug("Will not perform files cleanup, directory is supposed to be clean from newly created and modified files");
        }
        return;
      }
      snapshot = getSnapshot();
    } finally {
      myPropertiesProcessor.deleteRecord(myCheckoutDir);
    }

    myLogger.swabraDebug("Previous build files cleanup is performed before build");
    final FilesCollector.CollectionResult result = myFilesCollector.collect(snapshot, myCheckoutDir);

    switch (result) {
      case FAILURE:
        doCleanup(myCheckoutDir);
        return;

      case RETRY:
        myPropertiesProcessor.markDirty(myCheckoutDir);
        myMode = null;
        if (strict) {
          fail();
        }
    }
  }

  private FilesCollector initFilesCollector(BuildProgressLogger buildLogger, boolean verbose, boolean strict) {
    final LockedFileResolver lockedFileResolver =
      ((myHandlePath == null) || (!unifyPath(myHandlePath).endsWith(File.separator + HANDLE_EXE))) ?
        null : new LockedFileResolver(new HandlePidsProvider(myHandlePath, buildLogger), buildLogger);

    final FilesCollectionProcessor processor = (System.getProperty(TEST_LOG) == null) ?
      new FilesCollectionProcessor(myLogger, lockedFileResolver, verbose, strict) :
      new FilesCollectionProcessorForTests(myLogger, lockedFileResolver, verbose, strict, System.getProperty(TEST_LOG));

    return new FilesCollector(processor, myLogger);
  }

  public void beforeRunnerStart(@NotNull final AgentRunningBuild runningBuild) {
    if (!isEnabled(myMode)) return;
    final String snapshotName = "" + myCheckoutDir.hashCode();
    if (!new SnapshotGenerator(myCheckoutDir, myTempDir, myLogger).generateSnapshot(snapshotName)) {
      myPropertiesProcessor.markDirty(myCheckoutDir);
      myMode = null;
    } else {
      myPropertiesProcessor.setSnapshot(myCheckoutDir, snapshotName);
    }
  }

  public void beforeBuildFinish(@NotNull final BuildFinishedStatus buildStatus) {
    if (myHandlePath != null) {
      ProcessExecutor.runHandleAcceptEula(myHandlePath, myCheckoutDir.getAbsolutePath(), myLogger.getBuildLogger());
    }
    if (AFTER_BUILD.equals(myMode)) {
      myLogger.swabraMessage("Build files cleanup will be performed after build", true);
    }
  }

  public void buildFinished(@NotNull final BuildFinishedStatus buildStatus) {
    if (AFTER_BUILD.equals(myMode)) {
      myLogger.swabraDebug("Build files cleanup is performed after build");

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

  private File getSnapshot() {
    return new File(myTempDir, myPropertiesProcessor.getSnapshot(myCheckoutDir) + SNAPSHOT_SUFFIX);
  }

  private void logSettings(String mode, String checkoutDir, boolean strict, boolean lockingProcessesDetectionEnabled, String handlePath, boolean verbose) {
    myLogger.debug("Swabra settings: mode = '" + mode +
      "', checkoutDir = " + checkoutDir +
      "', strict = " + strict +
      "', locking processes detection = " + lockingProcessesDetectionEnabled +
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
        logger.swabraError("Interrupted while waiting for previous build files cleanup");
        logger.exception(e, true);
      }
    }
    myPrevThreads.remove(checkoutDir);
  }

  private void doCleanup(File checkoutDir) {
    myDirectoryCleaner.cleanFolder(checkoutDir, new SmartDirectoryCleanerCallback() {
      public void logCleanStarted(File dir) {
        myLogger.swabraMessage("Need a clean snapshot of checkout directory - forcing clean checkout for " + dir, true);
      }

      public void logFailedToDeleteEmptyDirectory(File dir) {
        myLogger.swabraWarn("Failed to delete empty checkout directory " + dir.getAbsolutePath());
      }

      public void logFailedToCleanFilesUnderDirectory(File dir) {
        myLogger.swabraWarn("Failed to delete files in directory " + dir.getAbsolutePath());

      }

      public void logFailedToCleanFile(File file) {
        myLogger.swabraWarn("Failed to delete file " + file.getAbsolutePath());
      }

      public void logFailedToCleanEntireFolder(File dir) {
        myLogger.swabraWarn("Failed to delete directory " + dir.getAbsolutePath());
        myPropertiesProcessor.markDirty(myCheckoutDir);
        myMode = null;
      }
    });
  }

  private void fail() {
    myLogger.message("##teamcity[buildStatus status='FAILURE' text='Swabra failed cleanup']", true);
  }

  private static String unifyPath(String path) {
    return path.replace("/", File.separator).replace("\\", File.separator);
  }

  private static boolean notDefined(String value) {
    return (value == null) || ("".equals(value));
  }

  private boolean prepareHandle() {
    try {
      if (notDefined(myHandlePath)) {
        myHandlePath = System.getProperty(HANDLE_EXE_SYSTEM_PROP);
        myLogger.swabraWarn("No Handle path passed in Swabra settings. Getting from system property "
          + HANDLE_EXE_SYSTEM_PROP);
        if (notDefined(myHandlePath)) {
          myLogger.swabraWarn("No Handle path passed in " + HANDLE_EXE_SYSTEM_PROP + " system property. Will not use Handle");
          return false;
        }
      }
      final File handleFile = new File(myHandlePath);
      if (!handleFile.isFile()) {
        myLogger.swabraWarn("No Handle executable found at " + myHandlePath);
        if (System.getProperty(DISABLE_DOWNLOAD_HANDLE) != null) {
          myLogger.swabraWarn("Will not download Handle executable from " + HANDLE_URL + ", (DISABLE_DOWNLOAD_HANDLE = \""
            + System.getProperty(DISABLE_DOWNLOAD_HANDLE) + "\")");
          return false;
        }
        myLogger.swabraWarn("Downloading Handle executable from " + HANDLE_URL);
        final File tmpFile = FileUtil.createTempFile("", ".zip");
        if (!URLDownloader.download(new URL(HANDLE_URL), tmpFile)) {
          return false;
        }
        ZipUtil.extract(tmpFile, handleFile.getParentFile(), new FilenameFilter() {
          public boolean accept(File dir, String name) {
            return HANDLE_EXE.equals(name);
          }
        });
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}