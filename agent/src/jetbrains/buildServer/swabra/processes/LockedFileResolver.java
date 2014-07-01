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

import jetbrains.buildServer.processes.ProcessFilter;
import jetbrains.buildServer.processes.ProcessTreeTerminator;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
  @NotNull
  private final WmicProcessDetailsProvider myWmicProcessDetailsProvider;
  @NotNull
  private final List<String> myIgnoredProcesses;

  public LockedFileResolver(@NotNull LockingProcessesProvider processesProvider,
                            @NotNull List<String> ignoredProcesses,
                            @NotNull WmicProcessDetailsProvider wmicProcessDetailsProvider) {
    myProcessesProvider = processesProvider;
    myWmicProcessDetailsProvider = wmicProcessDetailsProvider;
    myIgnoredProcesses = new ArrayList<String>(ignoredProcesses);
    myIgnoredProcesses.add(String.valueOf(ProcessTreeTerminator.getCurrentPid()));
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
    Collection<ProcessInfo> processes = getLockingProcesses(f, listener);
    if (processes == null) return false;

    if (processes.isEmpty()) {
      log("No processes found locking files in directory: " + f, false, listener);
      return false;
    } else {
      final StringBuilder sb = new StringBuilder("Found ").append(StringUtil.pluralize("process", processes.size())).append(" locking files in directory ").append(f).append(":");
      appendProcessInfos(processes, sb);
      log(sb.toString(), true, listener);
    }

    List<ProcessInfo> ignored = new ArrayList<ProcessInfo>();

    if (kill) {
      log("Will try to kill locking processes", false, listener);
      for (final ProcessInfo p : processes) {
        if (isIgnored(p)) {
          ignored.add(p);
          final StringBuilder sb = new StringBuilder("The following process and it's subtree has been skipped to avoid stopping of TeamCity agent:\n").append(getProcessString(p));
          log(sb.toString(), true, listener);
          continue;
        }

        try {
          if (ProcessTreeTerminator.kill(p.getPid(), ProcessFilter.MATCH_ALL)) {
            log("Process killed:\n" + getProcessString(p), false, listener);
          } else {
            logFailedToKill(p, null, listener);
          }
        } catch (Exception e) {
          logFailedToKill(p, e.getMessage(), listener);
        }

        Collection<ProcessInfo> aliveProcesses = getLockingProcesses(f, listener);
        if (aliveProcesses == null) return false;

        List<ProcessInfo> processesLeft = new ArrayList<ProcessInfo>();
        processesLeft.removeAll(ignored);

        if (processesLeft.isEmpty()) return true;

        final StringBuilder sb
          = new StringBuilder("Failed to kill some of the ").append(StringUtil.pluralize("process", processesLeft.size())).append(" locking files in directory: ").append(f).append(":");
        appendProcessInfos(processesLeft, sb);
        log(sb.toString(), true, listener);
        return false;
      }
    }
    return false;
  }

  @Nullable
  private Collection<ProcessInfo> getLockingProcesses(@NotNull File f, @Nullable Listener listener) {
    try {
      return myProcessesProvider.getLockingProcesses(f);
    } catch (GetProcessesException e) {
      log(e.getMessage(), true, listener);
    }

    return null;
  }

  private boolean isIgnored(@NotNull final ProcessInfo p) {
    return myIgnoredProcesses.contains(String.valueOf(p.getPid())) || myIgnoredProcesses.contains(p.getName());
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

    FileUtil.delete(f, DELETION_TRIES);
    return !f.exists();
  }

  private void appendProcessInfos(@NotNull Collection<ProcessInfo> processes, @NotNull StringBuilder sb) {
    for (final ProcessInfo p : processes) {
      sb.append("\n").append(getProcessString(p.getPid(), p.getName()));
    }
  }

  @NotNull
  private String getProcessString(@NotNull ProcessInfo p) {
    return getProcessString(p.getPid(), p.getName());
  }

  @NotNull
  private String getProcessString(@NotNull Long pid, @Nullable String name) {
    final String processDetails = myWmicProcessDetailsProvider.getProcessDetails(pid);
    return "PID: " + pid + "\n" + StringUtil.trim(StringUtil.isEmptyOrSpaces(processDetails) ? name : processDetails);
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
}