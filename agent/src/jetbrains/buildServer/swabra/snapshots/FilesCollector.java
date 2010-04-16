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

import jetbrains.buildServer.swabra.SwabraLogger;
import jetbrains.buildServer.swabra.snapshots.iteration.FileSystemFilesIterator;
import jetbrains.buildServer.swabra.snapshots.iteration.FilesTraversal;
import jetbrains.buildServer.swabra.snapshots.iteration.SnapshotFilesIterator;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * User: vbedrosova
 * Date: 23.01.2010
 * Time: 14:32:04
 */
public class FilesCollector {
  private static final String NOT_DELETE_SNAPSHOT = "swabra.preserve.snapshot";

  public static enum CollectionResult {
    CLEAN,
    ERROR,
    DIRTY,
    LOCKED
  }

  @NotNull
  private final FilesCollectionProcessor myProcessor;
  @NotNull
  private final SwabraLogger myLogger;

  public FilesCollector(@NotNull FilesCollectionProcessor processor,
                        @NotNull SwabraLogger logger) {
    myLogger = logger;
    myProcessor = processor;
  }

  public CollectionResult collect(@NotNull File snapshot, @NotNull File checkoutDir) {
    if (!snapshot.exists() || (snapshot.length() == 0)) {
      logUnableCollect(snapshot, checkoutDir, "file doesn't exist");
      return CollectionResult.ERROR;
    }

    myLogger.activityStarted();
    myLogger.message("Scanning checkout directory " + checkoutDir + " for newly created, modified and deleted files...", true);

    try {
      iterateAndCollect(snapshot, checkoutDir);
    } catch (Exception e) {
      logUnableCollect(snapshot, checkoutDir, "Exception occurred: " + e.getMessage());
      return CollectionResult.ERROR;
    }

    final FilesCollectionProcessor.Results results = myProcessor.getResults();

    final int detectedNew = results.detectedNewAndDeleted + results.detectedNewAndUnableToDelete;
    final String message = "Detected " + results.detectedUnchanged + " unchanged " + getObjectsNumber(results.detectedUnchanged) +
      ", " + detectedNew + " newly created " + getObjectsNumber(detectedNew) +
      (detectedNew > 0 ? " (" + results.detectedNewAndDeleted + " of them deleted)" : "") +
      ", " + results.detectedModified + " modified " + getObjectsNumber(results.detectedModified) +
      ", " + results.detectedDeleted + " deleted " + getObjectsNumber(results.detectedDeleted);

    removeSnapshot(snapshot, checkoutDir);
    try {
      if (results.detectedNewAndUnableToDelete != 0) {
        myLogger.warn(message);
        return CollectionResult.LOCKED;
      }
      if (results.detectedDeleted > 0 || results.detectedModified > 0) {
        myLogger.warn(message);
        return CollectionResult.DIRTY;
      }
      myLogger.message(message, true);
      return CollectionResult.CLEAN;
    } finally {
      myLogger.activityFinished();
    }
  }

  private String getObjectsNumber(int number) {
    return number == 1 ? "object" : "objects";
  }

  private void removeSnapshot(File snapshot, File checkoutDir) {
    if (System.getProperty(NOT_DELETE_SNAPSHOT) != null) {
      myLogger.swabraDebug("Will not delete " + snapshot.getAbsolutePath()
        + " for directory " + checkoutDir.getAbsolutePath() + ", " + NOT_DELETE_SNAPSHOT + "property specified");
    } else if (!FileUtil.delete(snapshot)) {
      myLogger.swabraWarn("Unable to remove snapshot file " + snapshot.getAbsolutePath()
        + " for directory " + checkoutDir.getAbsolutePath());
    } else {
      myLogger.swabraDebug("Successfully removed snapshot file " + snapshot.getAbsolutePath()
        + " for directory " + checkoutDir.getAbsolutePath() + " after files collection");
    }
  }

  private void iterateAndCollect(File snapshot, File checkoutDir) throws Exception {
    final FilesTraversal traversal = new FilesTraversal();
    traversal.traverseCompare(new SnapshotFilesIterator(snapshot), new FileSystemFilesIterator(checkoutDir), myProcessor);
  }

  private void logUnableCollect(File snapshot, File checkoutDir, String message) {
    myLogger.swabraWarn("Unable to collect files in checkout directory " + checkoutDir.getAbsolutePath()
      + " from snapshot file " + snapshot.getAbsolutePath() +
      ((message != null ? ", " + message : "")));
  }
}