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

import org.jetbrains.annotations.NotNull;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.io.File;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;

import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.agent.SimpleBuildLogger;
import com.intellij.execution.configurations.GeneralCommandLine;

/**
 * User: vbedrosova
 * Date: 25.11.2009
 * Time: 18:26:49
 */

// Provides pids of processes which lock file by running handle.exe and parsing it's output
public class HandlePidsProvider implements LockedFileResolver.LockingPidsProvider {
  private static final Logger LOG = Logger.getLogger(LockedFileResolver.class);

  private static final String NO_RESULT = "No matching handles found.";
  private static final String PID = "pid: ";

  @NotNull private final String myHandleExePath;
  private final SimpleBuildLogger myLogger;  

  public HandlePidsProvider(@NotNull String handleExePath, SimpleBuildLogger logger) {
    myHandleExePath = handleExePath;
    myLogger = logger;
  }

  @NotNull
  public List<Long> getPids(@NotNull final File file) {
    final String params[] = {file.getAbsolutePath()};
    final ExecResult result = ProcessExecutor.run(myHandleExePath, params, null);
    if (result.getStdout().contains(NO_RESULT)) {
      return Collections.emptyList();
    }
    return getPidsFromStdout(result.getStdout(), file.getAbsolutePath());
  }

  private List<Long> getPidsFromStdout(String stdout, String path) {
    final List<Long> pids = new ArrayList<Long>();
    final BufferedReader reader = new BufferedReader(new StringReader(stdout));
    try {
      String line = reader.readLine();
      while (line != null) {
        final int pathIndex = line.indexOf(path); 
        if (pathIndex != -1) {
          line = line.substring(0, pathIndex).replaceAll("\\s+", " ");
          final int pidEndIndex = line.indexOf(PID) + PID.length();
          line = line.substring(pidEndIndex, line.indexOf(" ", pidEndIndex));
          try {
            pids.add(Long.parseLong(line));
          } catch (NumberFormatException e) {
            error("Unable to parse pid string " + line, e);
          }
        }
        line = reader.readLine();        
      }
      return pids;
    } catch (IOException e) {
      error("IOException when reading", e);
      return pids;
    } finally {
      try {
        reader.close();
      } catch (IOException e) {
        error("IOException when closing reader", e);
        return pids;
      }
    }
  }

  private void error(String message, Throwable t) {
    LOG.error(message);
    if (myLogger != null) {
      myLogger.error(message);
      if (t != null) {
        myLogger.exception(t);
      }
    }
  }
}
