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

import jetbrains.buildServer.agent.BuildProgressLogger;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;


/**
 * User: vbedrosova
 * Date: 01.06.2009
 * Time: 16:09:47
 */
public final class SwabraLogger {
  @NonNls
  private static final String AGENT_BLOCK = "agent";
  public static final String ACTIVITY_NAME = "Garbage clean";

  private final BuildProgressLogger myBuildLogger;
  private final Logger myClassLogger;

  public SwabraLogger(@NotNull final BuildProgressLogger buildLogger, @NotNull final Logger classLogger) {
    myBuildLogger = buildLogger;
    myClassLogger = classLogger;
  }

  public void log(@NotNull final String message, boolean useBuildLog) {
    myClassLogger.info(message);
    if (useBuildLog) {
      myBuildLogger.message(message);
    }
  }

  public void debug(@NotNull final String message, boolean useBuildLog) {
    myClassLogger.debug(message);
    if (useBuildLog) {
      myBuildLogger.message(message);      
    }
  }

  public void activityStarted() {
    myBuildLogger.activityStarted(ACTIVITY_NAME, AGENT_BLOCK);
  }

  public void activityFinished() {
    myBuildLogger.activityFinished(ACTIVITY_NAME, AGENT_BLOCK);
  }
}
