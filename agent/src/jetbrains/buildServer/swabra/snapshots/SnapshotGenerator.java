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

package jetbrains.buildServer.swabra.snapshots;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import jetbrains.buildServer.swabra.SwabraLogger;
import jetbrains.buildServer.swabra.SwabraUtil;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import jetbrains.buildServer.swabra.snapshots.iteration.FileSystemFilesIterator;
import jetbrains.buildServer.swabra.snapshots.iteration.FilesTraversal;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.swabra.snapshots.SnapshotUtil.getSnapshotEntry;
import static jetbrains.buildServer.swabra.snapshots.SnapshotUtil.getSnapshotHeader;

/**
 * User: vbedrosova
 * Date: 23.01.2010
 * Time: 14:04:16
 */
public class SnapshotGenerator {
  private final File myRootDir;
  private String myRootDirParent;
  private int mySavedObjects;

  private final SwabraLogger myLogger;

  public SnapshotGenerator(@NotNull File dir,
                           @NotNull SwabraLogger logger) {
    myRootDir = dir;
    myRootDirParent = SwabraUtil.unifyPath(dir.getParent());
    if (myRootDirParent.endsWith(File.separator)) {
      myRootDirParent = myRootDirParent.substring(0, myRootDirParent.length() - 1);
    }
    myLogger = logger;
  }

  public boolean generateSnapshot(@NotNull File snapshot) {
    if (snapshot.exists()) {
      myLogger.debug("Snapshot file " + snapshot.getName() + " exists, trying to delete");
      if (!FileUtil.delete(snapshot)) {
        myLogger.warn("Unable to delete " + snapshot.getName());
        return false;
      }
    }
    mySavedObjects = 0;
    myLogger.message("Saving " + myRootDir +
      " directory state to snapshot file " + snapshot.getName(), true);

    BufferedWriter writer = null;
    try {
      writer = new BufferedWriter(new FileWriter(snapshot));
      writer.write(getSnapshotHeader(myRootDirParent));

      iterateAndBuildSnapshot(writer);
      myLogger.debug("Successfully finished saving " + myRootDir +
        " directory state to snapshot file " + snapshot.getName() + ", saved " + mySavedObjects + " objects (including root dir)");
    } catch (Exception e) {
      myLogger.warn("Unable to save " + myRootDir.getAbsolutePath()
        + " directory state to snapshot file " + snapshot.getName() + getMessage(e));
      myLogger.exception(e);
      return false;
    } finally {
      try {
        if (writer != null) {
          writer.close();
        }
      } catch (IOException e) {
        myLogger.exception(e);
        return false;
      }
    }
    return true;
  }

  private String getMessage(Exception e) {
    return e.getMessage() == null ? "" : ": " + e.getMessage();
  }

  private void iterateAndBuildSnapshot(final BufferedWriter writer) throws Exception {
    final FilesTraversal tr = new FilesTraversal();
    tr.traverse(new FileSystemFilesIterator(myRootDir), new FilesTraversal.SimpleProcessor() {
      public void process(FileInfo file) throws Exception {
        writer.write(getSnapshotEntry(file, myRootDirParent));
        ++mySavedObjects;
      }
    });
  }
}
