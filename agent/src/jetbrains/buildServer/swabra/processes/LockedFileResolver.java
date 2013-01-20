/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import jetbrains.buildServer.processes.ProcessFilter;
import jetbrains.buildServer.processes.ProcessTreeTerminator;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: vbedrosova
 * Date: 25.11.2009
 * Time: 18:30:53
 */
public class LockedFileResolver {
  private static final Logger LOG = Logger.getLogger(LockedFileResolver.class);

  private static final int DELETION_TRIES = 10;

  public static interface LockingProcessesProvider {
    @NotNull
    Collection<ProcessInfo> getLockingProcesses(@NotNull File f) throws GetProcessesException;
  }

  public static interface Listener {
    void message(String m);

    void warning(String w);
  }

  @NotNull
  private final LockingProcessesProvider myProcessesProvider;
//  @NotNull
//  private final ProcessTerminator myProcessTerminator;

  public LockedFileResolver(@NotNull LockingProcessesProvider processesProvider/*,
                            @NotNull ProcessTerminator processTerminator,*/) {
    myProcessesProvider = processesProvider;
//    myProcessTerminator = processTerminator;
  }


  /**
   * Resolves locked file f by collecting all it's locking processes and trying to kill them if kill
   * parameter is true
   *
   * @param f        file that needs resolving
   * @param kill     indicates whether locking processes should be killed
   * @param listener listener for resolving process
   * @return true if f was successfully resolved (locking processes were collected
   *         and all of them were killed if corresponding option was specified)
   */
  public boolean resolve(@NotNull File f, boolean kill, @Nullable Listener listener) {
    Collection<ProcessInfo> processes;

    try {
      processes = myProcessesProvider.getLockingProcesses(f);
    } catch (GetProcessesException e) {
      log(e.getMessage(), true, listener);
      return false;
    }

    final String number = getProcessesNumber(processes.size());
    if (processes.isEmpty()) {
      log("Found no locking processes for " + f, false, listener);
      return false;
    } else {
      final StringBuilder sb = new StringBuilder("Found locking ").append(number).append(" for ").append(f).append(":");
      appendProcesses(processes, sb);
      log(sb.toString(), true, listener);
    }

    if (kill) {
      log("Locking processes killing is enabled. Will try to kill locking " + number, false, listener);
      for (final ProcessInfo p : processes) {
        try {
          if (ProcessTreeTerminator.kill(p.getPid(), ProcessFilter.MATCH_ALL)) {
            log("Killed process " + getProcessString(p), false, listener);
          } else {
            logFailedToKill(p, null, listener);
          }
        } catch (Exception e) {
          logFailedToKill(p, e.getMessage(), listener);
        }
      }

      Collection<ProcessInfo> aliveProcesses;

      try {
        aliveProcesses = myProcessesProvider.getLockingProcesses(f);
      } catch (GetProcessesException e) {
        log(e.getMessage(), true, listener);
        return false;
      }

      if (aliveProcesses.isEmpty()) {
        return true;
      } else {
        final StringBuilder sb
          = new StringBuilder("Failed to kill locking ").append(getProcessesNumber(aliveProcesses.size())).append(" for ").append(f).append(":");
        appendProcesses(aliveProcesses, sb);
        log(sb.toString(), true, listener);
        return false;
      }
    }
    return false;
  }

  private void logFailedToKill(@NotNull final ProcessInfo p, @Nullable final String message, @Nullable final Listener listener) {
    log("Failed to kill process " + getProcessString(p) + (message != null ? ": " + message : ""), true, listener);
  }

  /**
   * Resolves locked file and tries to delete it
   *
   * @param f        file that needs resolving and deletion
   * @param listener listener for resolving process
   * @return true if f was deleted
   */
  public boolean resolveDelete(@NotNull File f, @Nullable Listener listener) {
    resolve(f, true, listener);

    int i = 0;
    while (i < DELETION_TRIES) {
      if (!f.exists() || FileUtil.delete(f)) {
        return true;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        //
      }
      ++i;
    }
    return false;
  }

  private void appendProcesses(Collection<ProcessInfo> processes, StringBuilder sb) {
    for (final ProcessInfo p : processes) {
      sb.append("\n").append(getProcessString(p));
    }
  }

  private String getProcessString(@NotNull ProcessInfo p) {
    return "PID:" + p.getPid() + " " + (p.getName() == null ? "" : p.getName());
  }

  private void log(@NotNull String m, boolean isWarning, @Nullable Listener listener) {
    if (isWarning) {
      LOG.warn(m);
      if (listener != null) listener.warning(m);
    } else {
      LOG.info(m);
      if (listener != null) listener.message(m);
    }
  }

  private String getProcessesNumber(int number) {
    return number == 1 ? "process" : "processes";
  }
}