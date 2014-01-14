/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.agent.BuildProgressLogger;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * User: vbedrosova
 * Date: 01.06.2009
 * Time: 16:09:47
 */
public final class SwabraLogger {
  @NonNls
  private static final String AGENT_BLOCK = "agent";
  private static final String ACTIVITY_NAME = "Swabra";

  public static final Logger CLASS_LOGGER = Logger.getLogger(SwabraLogger.class);
  @Nullable
  private BuildProgressLogger myBuildLogger;

  public void setBuildLogger(@Nullable BuildProgressLogger buildLogger) {
    myBuildLogger = buildLogger;
  }

  public void message(@NotNull final String message, boolean useBuildLog) {
    CLASS_LOGGER.info(message);
    if (useBuildLog && myBuildLogger != null) myBuildLogger.message(message);
  }
  public void warn(@NotNull final String message) {
    CLASS_LOGGER.warn(message);
    if (myBuildLogger != null) myBuildLogger.warning(message);
  }

  public void error(@NotNull String message) {
    CLASS_LOGGER.error(message);
    if (myBuildLogger != null) myBuildLogger.error(message);
  }

  public void debug(@NotNull final String message) {
    CLASS_LOGGER.debug(message);
  }

  public void exception(@NotNull Throwable e) {
    CLASS_LOGGER.warn(e.getMessage(), e);
  }

  public void activityStarted() {
    if (myBuildLogger != null) activityStarted(myBuildLogger);
  }

  public static void activityStarted(@NotNull BuildProgressLogger logger) {
    logger.activityStarted(ACTIVITY_NAME, AGENT_BLOCK);
  }

  public void activityFinished() {
    if (myBuildLogger != null) myBuildLogger.activityFinished(ACTIVITY_NAME, AGENT_BLOCK);
  }

  public void failBuild() {
    if (myBuildLogger == null) return;

    final String message = "Swabra cleanup failed";
    myBuildLogger.error(message + ": some files are locked");
    myBuildLogger.logBuildProblem(BuildProblemData.createBuildProblem(ACTIVITY_NAME, ACTIVITY_NAME, message));
  }
}
