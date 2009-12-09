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

package jetbrains.buildServer.swabra.processes;

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.agent.SimpleBuildLogger;
import org.jetbrains.annotations.NotNull;
import org.apache.log4j.Logger;

import java.io.File;

import com.intellij.execution.configurations.GeneralCommandLine;

/**
 * User: vbedrosova
 * Date: 01.12.2009
 * Time: 11:35:14
 */
public class ProcessExecutor {
  private static final Logger LOG = Logger.getLogger(ProcessExecutor.class);

  private static final int TIMEOUT = 1000;

  public static ExecResult run(@NotNull final String exePath, @NotNull String[] params, final SimpleBuildLogger logger) {
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(exePath);
    commandLine.addParameters(params);

    final ExecResult result =
    SimpleCommandLineProcessRunner.runCommand(commandLine, null, new SimpleCommandLineProcessRunner.RunCommandEvents() {

      public void onProcessStarted(Process ps) {
        info("Started " + commandLine.getCommandLineString(), logger);
      }

      public void onProcessFinished(Process ps) {
        info("Finished " + commandLine.getCommandLineString(), logger);
      }

      public Integer getOutputIdleSecondsTimeout() {
        return TIMEOUT;
      }
    });

    info("Stdout\n" + result.getStdout(), logger);
    final String stdErr = result.getStderr();
    if (stdErr.length() > 0) {
      error("Stderr\n" + stdErr, null);
    }
    return result;
  }

  private static void info(String message, SimpleBuildLogger logger) {
    LOG.info(message);
    if (logger != null) {
      logger.message(message);
    }
  }

  private static void error(String message, SimpleBuildLogger logger) {
    LOG.error(message);
    if (logger != null) {
      logger.error(message);
    }
  }
}
