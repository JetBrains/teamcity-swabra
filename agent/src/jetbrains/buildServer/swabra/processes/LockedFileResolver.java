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

import jetbrains.buildServer.processes.ProcessFilter;
import jetbrains.buildServer.processes.ProcessTreeTerminator;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * User: vbedrosova
 * Date: 25.11.2009
 * Time: 18:30:53
 */
public class LockedFileResolver {
  private static final Logger LOG = Logger.getLogger(LockedFileResolver.class);

  private static final int DELETION_TRIES = 10;

  public static interface LockingPidsProvider {
    @NotNull
    List<Long> getPids(@NotNull File f) throws GetPidsException;
  }

  public static interface Listener {
    void message(String m);

    void warning(String w);
  }

  @NotNull
  private final LockingPidsProvider myPidsProvider;
//  @NotNull
//  private final ProcessTerminator myProcessTerminator;

  public LockedFileResolver(@NotNull LockingPidsProvider pidsProvider/*,
                            @NotNull ProcessTerminator processTerminator,*/) {
    myPidsProvider = pidsProvider;
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
    List<Long> pids;

    try {
      pids = myPidsProvider.getPids(f);
    } catch (GetPidsException e) {
      log(e.getMessage(), true, listener);
      return false;
    }

    final String number = getProcessesNumber(pids.size());
    if (pids.isEmpty()) {
      log("Found no locking processes for " + f, false, listener);
      return false;
    } else {
      final StringBuffer message = new StringBuffer("Found locking ").append(number).append(" for ").append(f).append(": ");
      appendPids(pids, message);
      log(message.toString(), true, listener);
    }

    if (kill) {
      for (final long pid : pids) {
        ProcessTreeTerminator.kill(pid, ProcessFilter.MATCH_ALL);
      }
//      for (final long pid : pids) {
//        myProcessTerminator.kill(pid, ProcessFilter.MATCH_ALL);
//      }

      List<Long> alivePids;

      try {
        alivePids = myPidsProvider.getPids(f);
      } catch (GetPidsException e) {
        log(e.getMessage(), true, listener);
        return false;
      }

      if (alivePids.isEmpty()) {
        log("Killed locking " + number + " for " + f, false, listener);
        return true;
      } else {
        final StringBuffer message = new StringBuffer("Unable to kill locking " + number + " for ").append(f).append(": ");
        appendPids(alivePids, message);
        log(message.toString(), true, listener);
        return false;
      }
    }
    return false;
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
        log("Deleted " + f + " after resolving", false, listener);
        return true;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        //
      }
      ++i;
    }
    log("Unable to delete " + f, false, listener);
    return false;
  }

  private void appendPids(List<Long> processes, StringBuffer message) {
    for (final long i : processes) {
      message.append(" [#").append(i).append("] ");
    }
  }

  private void log(String m, boolean isWarning, Listener listener) {
    if (isWarning) {
      LOG.warn(m);
      listener.warning(m);
    } else {
      LOG.info(m);
      listener.message(m);
    }
  }

  private String getProcessesNumber(int number) {
    return number == 1 ? "process" : "processes";
  }
}