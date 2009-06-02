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

import com.intellij.openapi.util.io.FileUtil;


public final class Swabra extends AgentLifeCycleAdapter {
  private final DirectorySnapshot myDirectorySnapshot;
  private SwabraLogger myLogger;

  private String myMode;
  private File myCheckoutDir;
  private boolean myVerbose;

  private static boolean isEnabled(final String currMode) {
    return BEFORE_BUILD.equals(currMode) || AFTER_BUILD.equals(currMode);
  }

  private static boolean needFullCleanup(final String prevMode) {
    return !isEnabled(prevMode);
  }

  public Swabra(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher) {
    agentDispatcher.addListener(this);
    myDirectorySnapshot = new MapDirectorySnapshot();
  }

  public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
    final Map<String, String> runnerParams = runningBuild.getRunnerParameters();
    myLogger = new SwabraLogger(runningBuild.getBuildLogger(), Logger.getLogger(Swabra.class));
    myCheckoutDir = runningBuild.getCheckoutDirectory();
    myVerbose = isVerbose(runnerParams);
    final String mode = getSwabraMode(runnerParams);

    try {
      if (!isEnabled(mode)) {
        myLogger.log("Swabra is disabled", false);
        return;
      }
      logSettings(mode, myCheckoutDir.getAbsolutePath(), myVerbose);
      if (runningBuild.isCleanBuild()) {
        return;
      }
      if (needFullCleanup(myMode)) {
        myLogger.log("It is the first build with Swabra turned on - need full cleanup", false);
        // TODO: may be ask for clean build
        if (!FileUtil.delete(myCheckoutDir)) {
          myLogger.log("Unable to remove checkout directory on swabra work start", false);
        }
        return;
      }
      if (BEFORE_BUILD.equals(mode)) {
        if (AFTER_BUILD.equals(myMode)) {
          myLogger.log("Will not perform build garbage cleanup, as it occured on previous build finish", false);
        } else {
          myLogger.log("Previous build garbage cleanup is performed before build", false);
          collectGarbage(myCheckoutDir, myLogger, myVerbose);
        }
      } else if (AFTER_BUILD.equals(mode) && BEFORE_BUILD.equals(myMode)) {
        // mode setting changed from "before build" to "after build"
        myLogger.log("Swabra mode setting changed from \"before build\" to \"after build\", " +
                    "need to perform build garbage clean up once before build", false);
        collectGarbage(myCheckoutDir, myLogger, false);
      }
    } finally {
      myMode = mode;
    }
  }

  public void beforeRunnerStart(@NotNull final AgentRunningBuild runningBuild) {
    if (!isEnabled(myMode)) return;
    snapshot(myCheckoutDir, myLogger, myVerbose);
  }

  public void buildFinished(@NotNull final BuildFinishedStatus buildStatus) {
    if (AFTER_BUILD.equals(myMode)) {
      myLogger.log("Build garbage cleanup is performed after build", false);
      collectGarbage(myCheckoutDir, myLogger, myVerbose);
    }
  }

  private void snapshot(final File dir, @NotNull final SwabraLogger logger, boolean verbose) {
    if (dir == null || !dir.isDirectory()) {
      logger.log("Unable to save directory state, illegal checkout directory", false);
      return;
    }
    myLogger.log(getTime() + ": Saving checkout directory state...", false);
    myDirectorySnapshot.snapshot(dir, logger, verbose);
    myLogger.log(getTime() + ": Finished saving checkout directory state", false);
  }

  private void collectGarbage(final File dir, @NotNull final SwabraLogger logger, boolean verbose) {
    if (dir == null || !dir.isDirectory()) {
      logger.log("Unable to collect garbage, illegal checkout directory", false);
      return;
    }
    myLogger.log(getTime() + ": Collecting build garbage...", false);
    myDirectorySnapshot.collectGarbage(dir, logger, verbose);
    myLogger.log(getTime() + ": Finished collecting build garbage", false);
  }

  private void logSettings(String mode, String checkoutDir, boolean verbose) {
    myLogger.log("Swabra settings: mode = '" + mode +
                "', checkoutDir = " + checkoutDir +
                "', verbose = '" + verbose + "'.", false);
  }

  private long getTime() {
    return new Date().getTime();
  }
}
