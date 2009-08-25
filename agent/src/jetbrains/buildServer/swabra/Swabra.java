/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import static jetbrains.buildServer.swabra.SwabraUtil.*;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Map;
import java.util.Date;


public final class Swabra extends AgentLifeCycleAdapter {
  public static final String WORK_DIR_PROP = "agent.work.dir";

  private DirectorySnapshot myDirectorySnapshot;
  private SwabraLogger myLogger;
  private SmartDirectoryCleaner myDirectoryCleaner;

  private String myMode;
  private File myCheckoutDir;
  private boolean myVerbose;

  private static boolean isEnabled(final String currMode) {
    return BEFORE_BUILD.equals(currMode) || AFTER_BUILD.equals(currMode);
  }

  private static boolean needFullCleanup(final String prevMode) {
    return !isEnabled(prevMode);
  }

//  private static boolean needFullCleanup(final @NotNull File currCheckoutDir, final File prevCheckoutDir,
//                                         final String currMode, final String prevMode) {
//    return !isEnabled(prevMode)
//            || AFTER_BUILD.equals(currMode) && checkoutDirChanged(currCheckoutDir, prevCheckoutDir);
//  }

  private static boolean checkoutDirChanged(final @NotNull File currCheckoutDir, final File prevCheckoutDir) {
    return !currCheckoutDir.equals(prevCheckoutDir);
  }

  public Swabra(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher,
                @NotNull final SmartDirectoryCleaner directoryCleaner) {
    agentDispatcher.addListener(this);
    myDirectoryCleaner = directoryCleaner;
//    myDirectorySnapshot = new FileDirectorySnapshot(new File("c:\\TeamCity\\buildAgent\\work\\"));
  }

  public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
    final Map<String, String> runnerParams = runningBuild.getRunnerParameters();
    myLogger = new SwabraLogger(runningBuild.getBuildLogger(), Logger.getLogger(Swabra.class));
    myVerbose = isVerbose(runnerParams);
    final String mode = getSwabraMode(runnerParams);
    final File checkoutDir =  runningBuild.getCheckoutDirectory();
    try {
      if (!isEnabled(mode)) {
        myLogger.debug("Swabra is disabled", false);
        return;
      }
      logSettings(mode, checkoutDir.getAbsolutePath(), myVerbose);
      if (runningBuild.isCleanBuild()) {
        return;
      }
      if (needFullCleanup(myMode)) {
//      if (needFullCleanup(checkoutDir, myCheckoutDir, mode, myMode)) {
        myLogger.debug("It is the first build with Swabra turned on - need full cleanup", false);
//    myDirectorySnapshot = new MapDirectorySnapshot();
        myDirectorySnapshot = new FileDirectorySnapshot(new File(runningBuild.getBuildParameters().getSystemProperties().get(WORK_DIR_PROP)));
        myDirectoryCleaner.cleanFolder(checkoutDir, new SmartDirectoryCleanerCallback() {
          public void logCleanStarted(File dir) {
            myLogger.debug("Swabra trigged checkout clean checkout", false);
          }
          public void logFailedToDeleteEmptyDirectory(File dir) {
            myLogger.debug("Failed to delete empty directory " + dir.getAbsolutePath(), false);
          }
          public void logFailedToCleanFilesUnderDirectory(File dir) {
            myLogger.debug("Failed to delete files in directory " + dir.getAbsolutePath(), false);

          }
          public void logFailedToCleanFile(File file) {
            myLogger.debug("Failed to delete file " + file.getAbsolutePath(), false);
          }
          public void logFailedToCleanEntireFolder(File dir) {
            myLogger.debug("Failed to delete directory " + dir.getAbsolutePath(), false);
          }
        });
        return;
      }
      if (BEFORE_BUILD.equals(mode)) {
        if (AFTER_BUILD.equals(myMode) && !checkoutDirChanged(checkoutDir, myCheckoutDir)) {
          myLogger.debug("Will not perform build garbage cleanup, as it occured on previous build finish", false);
        } else {
          myLogger.debug("Previous build garbage cleanup is performed before build", false);
          collectGarbage(checkoutDir, myLogger, myVerbose);
        }
      } else if (AFTER_BUILD.equals(mode) && BEFORE_BUILD.equals(myMode)) {
        if (!checkoutDirChanged(checkoutDir, myCheckoutDir)) {
          // mode setting changed from "before build" to "after build"
          myLogger.debug("Swabra mode setting changed from \"before build\" to \"after build\", " +
                      "need to perform build garbage clean up once before build", false);
          collectGarbage(checkoutDir, myLogger, false);          
        } else {
          myLogger.debug("Swabra mode setting possibility changed from \"before build\" to \"after build\"", false);
        }
      }
    } finally {
      myMode = mode;
      myCheckoutDir = checkoutDir;
    }
  }

  public void beforeRunnerStart(@NotNull final AgentRunningBuild runningBuild) {
    if (!isEnabled(myMode)) return;
    snapshot(myCheckoutDir, myLogger, myVerbose);
  }

  public void buildFinished(@NotNull final BuildFinishedStatus buildStatus) {
    if (AFTER_BUILD.equals(myMode)) {
      myLogger.debug("Build garbage cleanup is performed after build", false);
      collectGarbage(myCheckoutDir, myLogger, myVerbose);
    }
  }

  private void snapshot(final File dir, @NotNull final SwabraLogger logger, boolean verbose) {
    if (dir == null || !dir.isDirectory()) {
      logger.debug("Unable to save directory state, illegal checkout directory - "
                    + ((dir == null) ? "null" : dir.getAbsolutePath()), false);
      return;
    }
    myLogger.log("Saving state of checkout directory " + dir.getAbsolutePath(), false);
    myDirectorySnapshot.snapshot(dir, logger, verbose);
    myLogger.log("Finished saving state of checkout directory " + dir.getAbsolutePath(), false);
  }

  private void collectGarbage(final File dir, @NotNull final SwabraLogger logger, boolean verbose) {
    if (dir == null || !dir.isDirectory()) {
      logger.debug("Unable to collect garbage, illegal checkout directory - "
                    + ((dir == null) ? "null" : dir.getAbsolutePath()), false);
      return;
    }
    myLogger.log("Collecting build garbage...", false);
    myDirectorySnapshot.collectGarbage(dir, logger, verbose);
    myLogger.log("Finished collecting build garbage", false);
  }

  private void logSettings(String mode, String checkoutDir, boolean verbose) {
    myLogger.debug("Swabra settings: mode = '" + mode +
                "', checkoutDir = " + checkoutDir +
                "', verbose = '" + verbose + "'.", false);
  }
}
