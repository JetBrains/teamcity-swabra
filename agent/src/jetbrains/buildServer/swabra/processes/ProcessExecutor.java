/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.swabra.processes;

import com.intellij.execution.configurations.GeneralCommandLine;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 01.12.2009
 * Time: 11:35:14
 */
public class ProcessExecutor {
  private static final Logger LOG = Logger.getLogger(ProcessExecutor.class);

  private static final int TIMEOUT = 1000;
  private static final String ACCEPT_EULA_KEY = "/accepteula";

  public static ExecResult runHandleAcceptEula(@NotNull final String handleExePath, @NotNull String file) {
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(handleExePath);
    commandLine.addParameter(ACCEPT_EULA_KEY);
    commandLine.addParameter(file);

    return run(commandLine);
  }

//  public static ExecResult run(@NotNull String exePath, @NotNull String[] params, SimpleBuildLogger logger) {
//    final GeneralCommandLine commandLine = new GeneralCommandLine();
//    commandLine.setExePath(exePath);
//    commandLine.addParameters(params);
//
//    return run(commandLine, logger);
//  }

  private static ExecResult run(final GeneralCommandLine commandLine) {
    return SimpleCommandLineProcessRunner.runCommand(commandLine, null, new SimpleCommandLineProcessRunner.ProcessRunCallback() {

      public void onProcessStarted(Process ps) {
        LOG.debug("Started " + commandLine.getCommandLineString());
      }

      public void onProcessFinished(Process ps) {
        LOG.debug("Finished " + commandLine.getCommandLineString());
      }

      public Integer getOutputIdleSecondsTimeout() {
        return TIMEOUT;
      }

      public Integer getMaxAcceptedOutputSize() {
        return null;
      }
    });
  }

//  private static void info(String message, SimpleBuildLogger logger) {
//    LOG.info(message);
//    if (logger != null) {
//      logger.message(message);
//    }
//  }
//
//  private static void error(String message, SimpleBuildLogger logger) {
//    LOG.error(message);
//    if (logger != null) {
//      logger.error(message);
//    }
//  }
}
