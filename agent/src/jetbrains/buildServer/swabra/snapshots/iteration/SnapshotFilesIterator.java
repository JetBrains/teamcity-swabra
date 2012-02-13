/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.swabra.snapshots.iteration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.swabra.snapshots.SnapshotUtil.*;

/**
 * User: vbedrosova
 * Date: 30.01.2010
 * Time: 16:13:55
 */
public class SnapshotFilesIterator implements FilesIterator {
  private static final Logger LOG = Logger.getLogger(SnapshotFilesIterator.class);

  @NotNull
  private final File mySnapshot;
  private BufferedReader myReader;

  private String myRootFolder;
  private String myCurrentDir;

  public SnapshotFilesIterator(@NotNull File snapshot) {
    mySnapshot = snapshot;
  }

  @Nullable
  public FileInfo getNext() {
    try {
      if (myReader == null) {
        myReader = new BufferedReader(new FileReader(mySnapshot));
        myRootFolder = myReader.readLine();
        myCurrentDir = "";
      }
      return processNextRecord();
    } catch (IOException e) {
      LOG.error("Error occurred when reading from input stream", e);
      closeReader();
      return null;
    }
  }

  private FileInfo processNextRecord() throws IOException {
    String fileRecord = myReader.readLine();
    if (fileRecord != null) {
      final String path = getFilePath(fileRecord);
      final long length = getFileLength(fileRecord);
      final long lastModified = getFileLastModified(fileRecord);

      if (path.endsWith("/") || path.endsWith("\\")) {
        myCurrentDir = myRootFolder + path;
        return new FileInfo(myCurrentDir.substring(0, myCurrentDir.length() - 1), length, lastModified, false);
      } else {
        return new FileInfo(myCurrentDir + path, length, lastModified, true);
      }
    }
    myReader.close();
    return null;
  }

  private void closeReader() {
    if (myReader == null) {
      return;
    }
    try {
      myReader.close();
    } catch (IOException e) {
      LOG.error("Error occurred when closing reader", e);
    }
  }
}
