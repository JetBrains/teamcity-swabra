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

package jetbrains.buildServer.swabra.processes;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import jetbrains.buildServer.ExecResult;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 25.11.2009
 * Time: 18:26:49
 */

// Provides pids and names of processes which lock file by running handle.exe and parsing it's output
public class HandleProcessesProvider implements LockedFileResolver.LockingProcessesProvider {
  private static final Logger LOG = Logger.getLogger(LockedFileResolver.class);

  private static final String PID = "pid: ";

  @NotNull
  private final String myHandleExePath;

  public HandleProcessesProvider(@NotNull String handleExePath) {
    myHandleExePath = handleExePath;
  }

  @NotNull
  public Collection<ProcessInfo> getLockingProcesses(@NotNull final File file) throws GetProcessesException {
    final ExecResult result = ProcessExecutor.runHandleAcceptEula(myHandleExePath, file.getAbsolutePath());

    LOG.debug("handle.exe output:\n" + result.getStdout());

    if (HandleOutputReader.noResult(result.getStdout())) {
      LOG.debug("No matching handles found for " + file.getAbsolutePath());
      return Collections.emptyList();
    } else if (HandleOutputReader.noAdministrativeRights(result.getStdout())) {
      throw new GetProcessesException("Administrative privilege is required to run handle.exe");
    }
    return getPidsFromStdout(result.getStdout());
  }

  private Collection<ProcessInfo> getPidsFromStdout(String stdout) {
    final Set<ProcessInfo> pids = new HashSet<ProcessInfo>();
    HandleOutputReader.read(stdout, new HandleOutputReader.LineProcessor() {
      public void processLine(@NotNull String line) {
        final int pidStartIndex = line.indexOf(PID);
        if (pidStartIndex != -1) {
          final int pidEndIndex = line.indexOf(PID) + PID.length();
          try {
            pids.add(new ProcessInfo(Long.parseLong(line.substring(pidEndIndex, line.indexOf(" ", pidEndIndex)).trim()),
              line.substring(0, pidStartIndex).trim()));
          } catch (NumberFormatException e) {
            LOG.warn("Unable to parse pid string " + line, e);
          }
        }
      }
    });
    return pids;
  }
}
