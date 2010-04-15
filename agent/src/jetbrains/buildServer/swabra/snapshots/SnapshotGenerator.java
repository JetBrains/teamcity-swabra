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

package jetbrains.buildServer.swabra.snapshots;

import jetbrains.buildServer.swabra.Swabra;
import jetbrains.buildServer.swabra.SwabraLogger;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import jetbrains.buildServer.swabra.snapshots.iteration.FileSystemFilesIterator;
import jetbrains.buildServer.swabra.snapshots.iteration.FilesTraversal;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static jetbrains.buildServer.swabra.snapshots.SnapshotUtil.getSnapshotEntry;
import static jetbrains.buildServer.swabra.snapshots.SnapshotUtil.getSnapshotHeader;

/**
 * User: vbedrosova
 * Date: 23.01.2010
 * Time: 14:04:16
 */
public class SnapshotGenerator {
  private final File myTempDir;
  private final File myCheckoutDir;
  private String myCheckoutDirParent;
  private int mySavedObjects;

  private final SwabraLogger myLogger;

  public SnapshotGenerator(@NotNull File checkoutDir,
                           @NotNull File tempDir,
                           @NotNull SwabraLogger logger) {
    myTempDir = tempDir;
    myCheckoutDir = checkoutDir;
    myCheckoutDirParent = checkoutDir.getParent();
    if (myCheckoutDirParent.endsWith(File.separator)) {
      myCheckoutDirParent = myCheckoutDirParent.substring(0, myCheckoutDirParent.length() - 1);
    }
    myLogger = logger;
  }

  public boolean generateSnapshot(@NotNull String snapshotName) {
    final File snapshot = new File(myTempDir, snapshotName + Swabra.SNAPSHOT_SUFFIX);
    if (snapshot.exists()) {
      myLogger.swabraDebug("Snapshot file " + snapshot.getAbsolutePath() + " exists, try deleting");
      if (!FileUtil.delete(snapshot)) {
        myLogger.swabraWarn("Unable to delete " + snapshot.getAbsolutePath());
        return false;
      }
    }
    mySavedObjects = 0;
    myLogger.activityStarted();
    myLogger.message("Saving state of checkout directory " + myCheckoutDir +
      " to snapshot file " + snapshot.getAbsolutePath() + "...", true);

    BufferedWriter writer = null;
    try {
      writer = new BufferedWriter(new FileWriter(snapshot));
      writer.write(getSnapshotHeader(myCheckoutDirParent));

      iterateAndBuildSnapshot(writer);
      myLogger.debug("Successfully finished saving state of checkout directory " + myCheckoutDir +
        " to snapshot file " + snapshot.getAbsolutePath() + ", saved " + mySavedObjects + " objects");
    } catch (Exception e) {
      myLogger.warn("Unable to save snapshot of checkout directory '" + myCheckoutDir.getAbsolutePath()
        + "' to file " + snapshot.getAbsolutePath());
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
      } finally {
        myLogger.activityFinished();
      }
    }
    return true;
  }

  private void iterateAndBuildSnapshot(final BufferedWriter writer) throws Exception {
    final FilesTraversal tr = new FilesTraversal();
    tr.traverse(new FileSystemFilesIterator(myCheckoutDir), new FilesTraversal.SimpleProcessor() {
      public void process(FileInfo file) throws Exception {
        writer.write(getSnapshotEntry(file, myCheckoutDirParent));
        ++mySavedObjects;
      }
    });
  }
}
