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
    SUCCESS {
      @Override
      public String toString() {
        return "SUCCESS";
      }
    },
    FAILURE {
      @Override
      public String toString() {
        return "FAILURE";
      }
    },
    RETRY {
      @Override
      public String toString() {
        return "RETRY";
      }
    }
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
      logUnableCollect(snapshot, checkoutDir, null, "file doesn't exist");
      return CollectionResult.FAILURE;
    }

    myLogger.activityStarted();
    myLogger.message("Scanning checkout directory " + checkoutDir + " for newly created and modified files...", true);

    try {
      iterateAndCollect(snapshot, checkoutDir);
    } catch (Exception e) {
      logUnableCollect(snapshot, checkoutDir, e, null);
      return CollectionResult.FAILURE;
    }

    final FilesCollectionProcessor.Results results = myProcessor.getResults();

    if (results.detectedNewAndUnableToDelete == 0) {
      removeSnapshot(snapshot, checkoutDir);
    }

    final String message = results.detectedNewAndDeleted + " object(s) deleted, "
      + (results.detectedNewAndUnableToDelete == 0 ? "" : "unable to delete " + results.detectedNewAndUnableToDelete + " object(s), ")
      + results.detectedModified + " object(s) detected modified, "
      + results.detectedDeleted + " object(s) detected deleted";

    try {
      if (results.detectedNewAndUnableToDelete != 0) {
        myLogger.warn(message);
        return CollectionResult.RETRY;
      }
      if (results.detectedDeleted > 0 || results.detectedModified > 0) {
        myLogger.warn(message);
        return CollectionResult.FAILURE;
      }
      myLogger.message(message, true);
      return CollectionResult.SUCCESS;
    } finally {
      myLogger.activityFinished();
    }
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

  private void logUnableCollect(File snapshot, File checkoutDir, Exception e, String message) {
    myLogger.swabraWarn("Unable to collect files in checkout directory " + checkoutDir.getAbsolutePath()
      + " from snapshot file " + snapshot.getAbsolutePath() +
      ((message != null ? ", " + message : "")));
    if (e != null) {
      myLogger.exception(e, true);
    }
  }
}