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

import jetbrains.buildServer.agent.SimpleBuildLogger;
import jetbrains.buildServer.processes.ProcessFilter;
import jetbrains.buildServer.processes.ProcessTreeTerminator;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

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
    List<Long> getPids(@NotNull File f);
  }

  @NotNull
  private final LockingPidsProvider myPidsProvider;

  private final SimpleBuildLogger myLogger;

  public LockedFileResolver(@NotNull LockingPidsProvider pidsProvider, SimpleBuildLogger logger) {
    myPidsProvider = pidsProvider;
    myLogger = logger;
  }


  /**
   * Resolves locked file f by collecting all it's locking processes and trying to kill them if kill
   * parameter is true
   *
   * @param f    file that needs resolving
   * @param kill indicates whether locking processes should be killed
   * @return true if f was successfully resolved (locking processes were collected
   *         and all of them were killed if corresponding option was specified)
   */
  public boolean resolve(@NotNull File f, boolean kill) {
    final List<Long> pids = myPidsProvider.getPids(f);

    if (pids.isEmpty()) {
      warn("Found no locking processes for " + f);
      return false;
    } else {
      final StringBuffer message = new StringBuffer("Found locking process(es) for ").append(f).append(": ");
      appendPids(pids, message);
      warn(message.toString());
    }

    if (kill) {
      info("Try killing locking process(es) for " + f);
      for (final long pid : pids) {
        ProcessTreeTerminator.kill(pid, ProcessFilter.MATCH_ALL);
      }

      final List<Long> alivePids = myPidsProvider.getPids(f);

      if (alivePids.isEmpty()) {
        info("Killed all locking processes for " + f);
        return true;
      } else {
        final StringBuffer message = new StringBuffer("Unable to kill locking process(es) for ").append(f).append(": ");
        appendPids(alivePids, message);
        warn(message.toString());
        return false;
      }
    }
    return false;
  }

  /**
   * Resolves locked file and tries to delete it
   *
   * @param f file that needs resolving and deletion
   * @return true if f was deleted
   */
  public boolean resolveDelete(@NotNull File f) {
    resolve(f, true);

    int i = 0;
    while (i < DELETION_TRIES) {
      if (!f.exists() || FileUtil.delete(f)) {
        LOG.info("Deleted " + f + " after resolving");
        return true;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        //
      }
      ++i;
    }
    LOG.info("Unable to delete " + f);
    return false;
  }

  private void info(String message) {
    LOG.info(message);
    if (myLogger != null) {
      myLogger.message(message);
    }
  }

  private void warn(String message) {
    LOG.warn(message);
    if (myLogger != null) {
      myLogger.warning(message);
    }
  }

  private void appendPids(List<Long> processes, StringBuffer message) {
    for (final long i : processes) {
      message.append(" [#").append(i).append("] ");
    }
  }
}