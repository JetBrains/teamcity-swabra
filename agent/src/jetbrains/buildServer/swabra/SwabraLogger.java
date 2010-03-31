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

import jetbrains.buildServer.agent.BuildProgressLogger;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


/**
 * User: vbedrosova
 * Date: 01.06.2009
 * Time: 16:09:47
 */
public final class SwabraLogger {
  @NonNls
  private static final String AGENT_BLOCK = "agent";
  public static final String ACTIVITY_NAME = "Swabra";

  @NotNull
  private final Logger myClassLogger;
  private BuildProgressLogger myBuildLogger;

  public SwabraLogger(@NotNull final Logger classLogger) {
    myClassLogger = classLogger;
  }

  public void setBuildLogger(@NotNull BuildProgressLogger buildLogger) {
    myBuildLogger = buildLogger;
  }

  public BuildProgressLogger getBuildLogger() {
    return myBuildLogger;
  }

  public void message(@NotNull final String message, boolean useBuildLog) {
    myClassLogger.info(message);
    if (useBuildLog && myBuildLogger != null) {
      myBuildLogger.message(message);
    }
  }

  public void warn(@NotNull final String message) {
    myClassLogger.warn(message);
    if (myBuildLogger != null) {
      myBuildLogger.warning(message);
    }
  }

  public void debug(@NotNull final String message) {
    myClassLogger.debug(message);
  }

  public void swabraMessage(@NotNull String message, boolean useBuildLog) {
    message = prepareMessage(message);
    myClassLogger.info(message);
    if (useBuildLog && myBuildLogger != null) {
      myBuildLogger.message(message);
    }
  }

  public void swabraWarn(@NotNull String message) {
    message = prepareMessage(message);
    myClassLogger.warn(message);
    if (myBuildLogger != null) {
      myBuildLogger.warning(message);
    }
  }

  public void swabraError(@NotNull String message) {
    message = prepareMessage(message);
    myClassLogger.error(message);
    if (myBuildLogger != null) {
      myBuildLogger.error(message);
    }
  }

  public void swabraDebug(@NotNull String message) {
    myClassLogger.debug(prepareMessage(message));
  }

  public void exception(@NotNull Throwable e, boolean useBuildLog) {
    myClassLogger.warn(e.getMessage(), e);
    if (useBuildLog && myBuildLogger != null) {
      myBuildLogger.exception(e);
    }
  }

  public void activityStarted() {
    myBuildLogger.activityStarted(ACTIVITY_NAME, AGENT_BLOCK);
  }

  public void activityFinished() {
    myBuildLogger.activityFinished(ACTIVITY_NAME, AGENT_BLOCK);
  }

  private static String prepareMessage(String message) {
    return "Swabra: " + message;
  }
}
