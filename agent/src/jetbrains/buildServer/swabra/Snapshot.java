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

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import jetbrains.buildServer.util.FileUtil;

/**
 * User: vbedrosova
 * Date: 01.06.2009
 * Time: 17:09:05
 */
public final class Snapshot {
  private static final String FILE_SUFFIX = ".snapshot";
  private static final String SEPARATOR = "\t";

  private final File myTempDir;
  private final File myCheckoutDir;
  private String myCheckoutDirParent;

  private Map<String, FileInfo> myFiles;

  private final List<File> myAppeared = new ArrayList<File>();
  private final List<File> myModified = new ArrayList<File>();
  private final List<File> myUnableToDelete = new ArrayList<File>();


  public Snapshot(@NotNull File tempDir, @NotNull File checkoutDir) {
    myCheckoutDir = checkoutDir;
    myCheckoutDirParent = myCheckoutDir.getParent();
    if (myCheckoutDirParent.endsWith(File.separator)) {
      myCheckoutDirParent = myCheckoutDirParent.substring(0, myCheckoutDirParent.length() - 1);
    }
    myTempDir = tempDir;
  }

  public File getCheckoutDir() {
    return myCheckoutDir;
  }

  public boolean snapshot(@NotNull String snapshotName, @NotNull SwabraLogger logger, boolean verbose) {
    final File snapshot = new File(myTempDir, snapshotName + FILE_SUFFIX);
    if (myCheckoutDir == null || !myCheckoutDir.isDirectory()) {
      logger.error("Swabra: Unable to save directory state, illegal checkout directory - "
        + ((myCheckoutDir == null) ? "null" : myCheckoutDir.getAbsolutePath()));
      return false;
    }
    if (snapshot.exists()) {
      logger.debug("Swabra: Snapshot file " + snapshot.getAbsolutePath() + " exists, try deleting");        
      if (!FileUtil.delete(snapshot)) {
        logger.debug("Swabra: Unable to delete " + snapshot.getAbsolutePath());
      }
    }
    logger.message("Swabra: Saving state of checkout directory " + myCheckoutDir + " to snapshot file " + snapshot.getAbsolutePath(), true);
    BufferedWriter snapshotWriter = null;
    try {
      snapshotWriter = new BufferedWriter(new FileWriter(snapshot));
      snapshotWriter.write(myCheckoutDirParent + File.separator + "\r\n");
      snapshotWriter.write(myCheckoutDir.getName() + File.separator + SEPARATOR
        + myCheckoutDir.length() + SEPARATOR + myCheckoutDir.lastModified() + "\r\n");
      saveState(myCheckoutDir, snapshotWriter);
      logger.message("Swabra: Finished saving state of checkout directory " + myCheckoutDir + " to snapshot file " + snapshot.getAbsolutePath(), true);
    } catch (Exception e) {
      logger.error("Swabra: Unable to save checkout directory " + myCheckoutDir.getAbsolutePath()
        + " snapshot to file " + snapshot.getAbsolutePath());
      logger.exception(e, true);
      return false;
    } finally {
      try {
        if (snapshotWriter != null) {
          snapshotWriter.close();
        }
      } catch (IOException e) {
        logger.exception(e, true);
        return false;
      }
    }
    return true;
  }

  private void saveState(@NotNull File dir, @NotNull BufferedWriter snapshotWriter) throws Exception {
    final File[] files = dir.listFiles();
    if (files == null || files.length == 0) return;
    final List<File> dirs = new ArrayList<File>();
    for (File file : files) {
      if (file.isFile()) {
        saveFileState(file, snapshotWriter);
      } else {
        dirs.add(file);
      }
    }
    for (File d : dirs) {
      saveFileState(d, snapshotWriter);
      saveState(d, snapshotWriter);
    }
  }

  private void saveFileState(@NotNull final File file, @NotNull BufferedWriter snapshotWriter) throws Exception {
    final boolean isDir = file.isDirectory();
    String fPath = file.getAbsolutePath();
    fPath = isDir ? fPath.substring(fPath.indexOf(myCheckoutDirParent) + myCheckoutDirParent.length() + 1) : file.getName(); //+1 for trailing slash
    final String trailingSlash = isDir ? File.separator : "";
    snapshotWriter.write(fPath + trailingSlash + SEPARATOR
      + file.length() + SEPARATOR + file.lastModified() + "\r\n");
  }

  public boolean collect(@NotNull String snapshotName, @NotNull SwabraLogger logger, boolean verbose) {
    final File snapshot = new File(myTempDir, snapshotName + FILE_SUFFIX);
    if (myCheckoutDir == null || !myCheckoutDir.isDirectory()) {
      logger.error("Swabra: Unable to collect files, illegal checkout directory - "
        + ((myCheckoutDir == null) ? "null" : myCheckoutDir.getAbsolutePath()));
      return false;
    }
    if (!snapshot.exists() || (snapshot.length() == 0)) {
      logUnableCollect(logger, snapshot, null, "file doesn't exist");
      return false;
    }
    myFiles = new HashMap<String, FileInfo>();
    BufferedReader snapshotReader = null;
    try {
      snapshotReader = new BufferedReader(new FileReader(snapshot));
      final String parentDir = snapshotReader.readLine();
      String currentDir = "";
      String fileRecord = snapshotReader.readLine();
      while (fileRecord != null) {
        final int firstSeparator = fileRecord.indexOf(SEPARATOR);
        final int secondSeparator = fileRecord.indexOf(SEPARATOR, firstSeparator + 1);
        final String path = fileRecord.substring(0, firstSeparator);
        final FileInfo fi = new FileInfo(Long.parseLong(fileRecord.substring(firstSeparator + 1, secondSeparator)),
          Long.parseLong(fileRecord.substring(secondSeparator + 1)));
        if (path.endsWith(File.separator)) {
          currentDir = parentDir + path;
          myFiles.put(currentDir.substring(0, currentDir.length() - 1), fi);
        } else {
          myFiles.put(currentDir + path, fi);
        }
        fileRecord = snapshotReader.readLine();
      }
      return collectFiles(myCheckoutDir, logger, verbose);
    } catch (Exception e) {
      logUnableCollect(logger, snapshot, e, "exception when reading from file");
      return false;
    } finally {
      try {
        if (snapshotReader != null) {
          snapshotReader.close();
        }
        if (myUnableToDelete.isEmpty()) {
          if (!FileUtil.delete(snapshot)) {
            logger.error("Swabra: Unable to remove snapshot file " + snapshot.getAbsolutePath()
              + " for directory " + myCheckoutDir.getAbsolutePath());
          }
          myUnableToDelete.clear();
        }
        myAppeared.clear();
        myModified.clear();
        myFiles.clear();
      } catch (Exception e) {
        logUnableCollect(logger, snapshot, e, "exception when closing file");
        return false;
      }
    }
  }

  private void logUnableCollect(SwabraLogger logger, File snapshot, Exception e, String message) {
    logger.error("Swabra: Unable to collect files in checkout directory " + myCheckoutDir.getAbsolutePath()
      + " from snapshot file " + snapshot.getAbsolutePath() +
      ((message != null ? ", " + message : "")));
    if (e != null) {
      logger.exception(e, true);
    }
  }

  private boolean collectFiles(@NotNull final File dir, @NotNull final SwabraLogger logger, boolean verbose) {
    logger.message("Swabra: Scanning checkout directory " + myCheckoutDir + " for newly created and modified files...", true);
    logger.activityStarted();
    collect(dir, logger);
    logTotals(logger, verbose);
    logger.activityFinished();
    final String message = "Swabra: Finished scanning checkout directory " + myCheckoutDir
                          + " for newly created and modified files: "
                          + myAppeared.size() + " object(s) deleted, "
                          + (myUnableToDelete.isEmpty() ? "" : "unable to delete " + myUnableToDelete.size() + " object(s), ")
                          + myModified.size() + " object(s) detected modified";
    if (myUnableToDelete.isEmpty()) {
      logger.message(message, true);
      return true;
    } else {
      logger.warn(message);
      return false;
    }
  }

  private void collect(@NotNull final File dir, @NotNull SwabraLogger logger) {
    final File[] files = dir.listFiles();
    if (files == null || files.length == 0) return;
    for (File file : files) {
      final FileInfo info = myFiles.get(file.getAbsolutePath());
      if (info == null) {
        if (file.isDirectory()) {
          //all directory content is supposed to be garbage
          collect(file, logger);
        }
        if (!FileUtil.delete(file)) {
          myUnableToDelete.add(file);
        } else {
          myAppeared.add(file);          
        }
      } else if ((file.lastModified() != info.getLastModified()) || file.length() != info.getLength()) {
        myModified.add(file);
        if (file.isDirectory()) {
          //directory's content is supposed to be modified
          collect(file, logger);
        }
      } else {
        if (file.isDirectory()) {
          //yet know nothing about directory's content, so dig into
          collect(file, logger);
        }
      }
    }
  }

  private void logTotals(@NotNull SwabraLogger logger, boolean verbose) {
    String prefix = null;
    if (!myAppeared.isEmpty()) {
      prefix = "Deleting ";
      for (File file : myAppeared) {
        logger.message(prefix + file.getAbsolutePath(), verbose);
      }
    }
    if (!myUnableToDelete.isEmpty()) {
      prefix = "Unable o delete ";
      for (File file : myUnableToDelete) {
        logger.warn(prefix + file.getAbsolutePath());
      }
    }
    if (!myModified.isEmpty()) {
      prefix = "Detected modified ";
      for (File file : myModified) {
        logger.message(prefix + file.getAbsolutePath(), verbose);
      }
    }
    if (prefix == null) {
      logger.message("No newly created or modified files detected", verbose);
    }
  }

  private static final class FileInfo {
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
