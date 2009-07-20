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

/**
 * User: vbedrosova
 * Date: 01.06.2009
 * Time: 17:09:05
 */
public class FileDirectorySnapshot extends DirectorySnapshot {
  private static final String SEPARATOR = "\t"; 

  private final File myWorkingDir;
  private Writer mySnapshotWriter;
  private BufferedReader mySnapshotReader;

  public FileDirectorySnapshot(File workingDir) {
    myWorkingDir = workingDir;
  }

  public void snapshot(@NotNull File dir, @NotNull SwabraLogger logger, boolean verbose) {
    try {
      mySnapshotWriter = new FileWriter(new File(myWorkingDir, dir.getName() + ".snapshot"));
      mySnapshotWriter.write("#Don't edit this file!\n");
      mySnapshotWriter.write(dir.getParent() + File.separator + "\n");
      mySnapshotWriter.write(dir.getName() + File.separator + SEPARATOR
        + dir.length() +  SEPARATOR + dir.lastModified() + "\n");
      saveState(dir);
      mySnapshotWriter.close();
    } catch (Exception e) {
      logger.debug("Unable to save working directory snapshot to file", false);
    }
  }

  public void collectGarbage(@NotNull File dir, @NotNull SwabraLogger logger, boolean verbose) {
    final File snapshot = new File(myWorkingDir, dir.getName() + ".snapshot");
    if (!snapshot.exists()) {
      logger.debug("Unable to read working directory snapshot from file, no file exists", false);
      return;
    }
    myFiles = new HashMap<String, FileInfo>();
    try {
      mySnapshotReader = new BufferedReader(new FileReader(new File(myWorkingDir, dir.getName() + ".snapshot")));
      mySnapshotReader.readLine(); // read first comment
      final String parentDir = mySnapshotReader.readLine();
      String currentDir = "";
      String fileRecord = mySnapshotReader.readLine();
      while (fileRecord != null) {
        final int firstSeparator = fileRecord.indexOf(SEPARATOR);
        final int secondSeparator = fileRecord.indexOf(SEPARATOR, firstSeparator + 1);
        final String path = fileRecord.substring(0, firstSeparator);
        final String length = fileRecord.substring(firstSeparator + 1, secondSeparator);
        final String lastModified = fileRecord.substring(secondSeparator + 1);
        if (path.endsWith(File.separator)) {
          currentDir = parentDir + path;
          myFiles.put(currentDir.substring(0, currentDir.length() - 1), new FileInfo(Long.parseLong(length), Long.parseLong(lastModified)));          
        } else {
          myFiles.put(currentDir + path, new FileInfo(Long.parseLong(length), Long.parseLong(lastModified)));
        }
        fileRecord = mySnapshotReader.readLine();
      }
    } catch (Exception e) {
      logger.debug("Unable to read working directory snapshot from file", false);
      return;
    } finally {
      try {
        mySnapshotReader.close();
      } catch (IOException e) {
        logger.debug("Unable to read working directory snapshot from file", false);
      }
    }
    if (!snapshot.delete()) {
      logger.debug("Unable to delete file containig directory snapshot", false);
    }
    super.collectGarbage(dir, logger, verbose);
  }

  void saveFileState(@NotNull final File file) throws Exception {
    final boolean isDir = file.isDirectory(); 
    final String wdPath = myWorkingDir.getAbsolutePath();
    String fPath = file.getAbsolutePath();
    fPath = isDir ? fPath.substring(fPath.indexOf(wdPath) + wdPath.length() + 1) : file.getName(); //+1 for trailing slash
    final String trailingSlash = isDir ? File.separator : "";
    mySnapshotWriter.write(fPath + trailingSlash + SEPARATOR
      + file.length() +  SEPARATOR + file.lastModified() + "\n");
  }
}
