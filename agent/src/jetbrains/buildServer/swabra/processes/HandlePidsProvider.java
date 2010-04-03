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

package jetbrains.buildServer.swabra.processes;

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.agent.SimpleBuildLogger;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: vbedrosova
 * Date: 25.11.2009
 * Time: 18:26:49
 */

// Provides pids of processes which lock file by running handle.exe and parsing it's output
public class HandlePidsProvider implements LockedFileResolver.LockingPidsProvider {
  private static final Logger LOG = Logger.getLogger(LockedFileResolver.class);

  private static final String PID = "pid: ";

  @NotNull
  private final String myHandleExePath;
  private final SimpleBuildLogger myLogger;

  public HandlePidsProvider(@NotNull String handleExePath, SimpleBuildLogger logger) {
    myHandleExePath = handleExePath;
    myLogger = logger;
  }

  @NotNull
  public List<Long> getPids(@NotNull final File file) {
    final ExecResult result = ProcessExecutor.runHandleAcceptEula(myHandleExePath, file.getAbsolutePath());
    if (HandleOutputReader.noResult(result.getStdout())) {
      return Collections.emptyList();
    }
    return getPidsFromStdout(result.getStdout(), file.getAbsolutePath());
  }

  private List<Long> getPidsFromStdout(String stdout, final String path) {
    final List<Long> pids = new ArrayList<Long>();
    HandleOutputReader.read(stdout, new HandleOutputReader.LineProcessor() {
      public void processLine(@NotNull String line) {
        final int pathIndex = line.indexOf(path);
        if (pathIndex != -1) {
          line = line.substring(0, pathIndex).replaceAll("\\s+", " ");
          final int pidEndIndex = line.indexOf(PID) + PID.length();
          line = line.substring(pidEndIndex, line.indexOf(" ", pidEndIndex));
          try {
            pids.add(Long.parseLong(line));
          } catch (NumberFormatException e) {
            warning("Unable to parse pid string " + line, e);
          }
        }
      }
    });
    return pids;
  }

  private void warning(String message, Throwable t) {
    LOG.warn(message, t);
    if (myLogger != null) {
      myLogger.warning(message);
    }
  }
}
