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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jetbrains.buildServer.processes.ProcessFilter;
import jetbrains.buildServer.processes.ProcessNode;
import jetbrains.buildServer.processes.ProcessTreeTerminator;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
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
  @NotNull
  private final WmicProcessDetailsProvider myWmicProcessDetailsProvider;
  @NotNull
  private final List<String> myIgnoredProcesses;
//  @NotNull
//  private final ProcessTerminator myProcessTerminator;

  public LockedFileResolver(@NotNull LockingProcessesProvider processesProvider,
                            @NotNull List<String> ignoredProcesses,
                            @NotNull WmicProcessDetailsProvider wmicProcessDetailsProvider/*,
                            @NotNull ProcessTerminator processTerminator,*/) {
    myProcessesProvider = processesProvider;
    myWmicProcessDetailsProvider = wmicProcessDetailsProvider;
    myIgnoredProcesses = new ArrayList<String>(ignoredProcesses);
    myIgnoredProcesses.add(String.valueOf(ProcessTreeTerminator.getCurrentPid()));
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
      appendProcessInfos(processes, sb);
      log(sb.toString(), true, listener);
    }

    if (kill) {
      log("Locking processes killing is enabled. Will try to kill locking " + number, false, listener);
      for (final ProcessInfo p : processes) {

        final List<ProcessNode> ignored = getIgnored(p);
        if (ignored.isEmpty()) {
          try {
            if (ProcessTreeTerminator.kill(p.getPid(), ProcessFilter.MATCH_ALL)) {
              log("Killed process " + getProcessString(p), false, listener);
            } else {
              logFailedToKill(p, null, listener);
            }
          } catch (Exception e) {
            logFailedToKill(p, e.getMessage(), listener);
          }
        } else {
          final StringBuilder sb = new StringBuilder("Skip process ").append(getProcessString(p)).append(" and it's subtree as this may stop TeamCity agent. Suspicious ")
            .append(getProcessesNumber(ignored.size())).append(" from the subtree:");
          appendProcessNodes(ignored, sb);
          log(sb.toString(), true, listener);
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
        appendProcessInfos(aliveProcesses, sb);
        log(sb.toString(), true, listener);
        return false;
      }
    }
    return false;
  }

  @NotNull
  private List<ProcessNode> getIgnored(@NotNull ProcessInfo processInfo) {
    final Collection<ProcessNode> processes = ProcessTreeTerminator.getChildProcesses(processInfo.getPid(), ProcessFilter.MATCH_ALL);
    return CollectionsUtil.filterCollection(processes, new Filter<ProcessNode>() {
      public boolean accept(@NotNull final ProcessNode data) {
        return myIgnoredProcesses.contains(String.valueOf(data.getPid())) || myIgnoredProcesses.contains(data.getCommandLine());
      }
    });
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

  private void appendProcessInfos(@NotNull Collection<ProcessInfo> processes, @NotNull StringBuilder sb) {
    for (final ProcessInfo p : processes) {
      sb.append("\n").append(getProcessString(p.getPid(), p.getName()));
    }
  }

  private void appendProcessNodes(@NotNull Collection<ProcessNode> processes, @NotNull StringBuilder sb) {
    for (final ProcessNode p : processes) {
      sb.append("\n").append(getProcessString(p.getPid(), p.getCommandLine()));
    }
  }

  @NotNull
  private String getProcessString(@NotNull ProcessInfo p) {
    return getProcessString(p.getPid(), p.getName());
  }

  @NotNull
  private String getProcessString(@NotNull Long pid, @Nullable String name) {
    final String processDetails = myWmicProcessDetailsProvider.getProcessDetails(pid);
    return "PID:" + pid + "\n" + (StringUtil.isEmptyOrSpaces(processDetails) ? name : processDetails);
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