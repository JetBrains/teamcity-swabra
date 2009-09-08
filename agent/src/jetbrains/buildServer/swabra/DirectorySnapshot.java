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

package jetbrains.buildServer.swabra;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * User: vbedrosova
 * Date: 01.06.2009
 * Time: 15:58:12
 */
public abstract class DirectorySnapshot {
  protected Map<String, FileInfo> myFiles = null;

  protected final List<File> myAppeared = new ArrayList<File>();
  protected final List<File> myModified = new ArrayList<File>();


  public abstract void snapshot(@NotNull final File dir, @NotNull final SwabraLogger logger, boolean verbose);
  abstract void saveFileState(@NotNull final File file) throws Exception;

  protected void saveState(@NotNull final File dir)  throws Exception {
    final File[] files = dir.listFiles();
    if (files == null || files.length == 0) return;
    final List<File> dirs = new ArrayList<File>();
    for (File file : files) {
      if (file.isFile()) {
        saveFileState(file);
      } else {
        dirs.add(file);
      }
    }
    for (File d : dirs) {
      saveFileState(d);
      saveState(d);
    }
  }

  public void collectFiles(@NotNull final File dir, @NotNull final SwabraLogger logger, boolean verbose) {
    if (myFiles == null) {
      logger.debug("Unable to collect garbage, directory snapshot was not saved", false);
      return;
    }
    logger.activityStarted();
    collect(dir, logger);
    logTotals(logger, verbose);
    logger.activityFinished();
    myAppeared.clear();
    myModified.clear();
    myFiles = null;
  }

  protected void collect(@NotNull final File dir, @NotNull SwabraLogger logger) {
    final File[] files = dir.listFiles();
    if (files == null || files.length == 0) return;
    for (File file : files) {
      final FileInfo info = myFiles.get(file.getAbsolutePath());
      if (info == null) {
        myAppeared.add(file);
        if (file.isDirectory()) {
          //all directory content is supposed to be garbage
          collect(file, logger);
        }
        if (!file.delete()) {
          logger.debug("Unable to delete previous build garbage " + file.getAbsolutePath(), false);
        }
      } else if ((file.lastModified() != info.getLastModified()) ||
                  file.length() != info.getLength()) {
        myModified.add(file);
        if (file.isDirectory()) {
          //directory's content is supposed to be modified
          collect(file, logger);
        }
      }
    }
  }

  protected void logTotals(@NotNull SwabraLogger logger, boolean verbose) {
    String prefix = null;
    if (myAppeared.size() > 0) {
      prefix = "Deleting ";
      for (File file : myAppeared) {
        logger.debug(prefix + file.getAbsolutePath(), verbose);
      }
    }
    if (myModified.size() > 0) {
      prefix = "Detected modified ";
      for (File file : myModified) {
        logger.debug(prefix + file.getAbsolutePath(), verbose);
      }
    }
    if (prefix == null) {
      logger.debug("No garbage or modified files detected", verbose);
    }
  }

  protected static final class FileInfo {
    private final long myLength;
    private final long myLastModified;

    public FileInfo(long length, long lastModified) {
      myLength = length;
      myLastModified = lastModified;
    }

    public FileInfo(File f) {
      this(f.lastModified(), f.length());
    }

    public long getLastModified() {
      return myLastModified;
    }

    public long getLength() {
      return myLength;
    }
  }
}
