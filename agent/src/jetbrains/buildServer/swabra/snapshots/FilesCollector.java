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

import java.io.File;
import java.util.List;
import jetbrains.buildServer.swabra.SwabraLogger;
import jetbrains.buildServer.swabra.SwabraSettings;
import jetbrains.buildServer.swabra.SwabraUtil;
import jetbrains.buildServer.swabra.snapshots.iteration.FileSystemFilesIterator;
import jetbrains.buildServer.swabra.snapshots.iteration.FilesTraversal;
import jetbrains.buildServer.swabra.snapshots.iteration.SnapshotFilesIterator;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: vbedrosova
 * Date: 23.01.2010
 * Time: 14:32:04
 */
public class FilesCollector {
  private static final String NOT_DELETE_SNAPSHOT = "swabra.preserve.snapshot";

  public static interface CollectionResultHandler {
    void success();
    void error();
    void lockedFilesDetected();
    void dirtyStateDetected();
  }

  @NotNull
  private final FilesCollectionProcessor myProcessor;
  @NotNull
  private final SwabraLogger myLogger;

  @NotNull
  private final SwabraSettings mySettings;

  public FilesCollector(@NotNull FilesCollectionProcessor processor,
                        @NotNull SwabraLogger logger,
                        @NotNull SwabraSettings settings) {
    myLogger = logger;
    myProcessor = processor;
    mySettings = settings;
  }

  public void collect(@NotNull File snapshot, @NotNull File dir, @Nullable CollectionResultHandler handler) {
    if (!snapshot.exists() || (snapshot.length() == 0)) {
      logUnableCollect(snapshot, dir, "file doesn't exist", null);
      if (handler != null) handler.error();
      return;
    }

    final List<String> rules = mySettings.getRules().getRulesForPath(dir);
    myLogger.message("Scanning directory " + dir + " for newly created, modified and deleted files comparing with snapshot " +
      snapshot.getName() + (rules.size() == 1 ? "" : ", paths to monitor are " + SwabraUtil.getRulesStr(rules)), true);

    try {
      iterateAndCollect(snapshot, dir);
    } catch (Exception e) {
      logUnableCollect(snapshot, dir, "Exception occurred: " + e.getMessage(), e);
      if (handler != null) handler.error();
      return;
    }

    final FilesCollectionProcessor.Results results = myProcessor.getResults();

    final int detectedNew = results.detectedNewAndDeleted + results.detectedNewAndUnableToDelete;
    final String message = "Detected " + (results.detectedUnchanged >= 1 ? results.detectedUnchanged - 1 : 0) + " unchanged, " + // -1 not to count dir
      detectedNew + " newly created" +
      (detectedNew > 0 ? " (" + results.detectedNewAndDeleted + " of them deleted), " : ", ") +
      results.detectedModified + " modified, " +
      results.detectedDeleted + " deleted files and directories";

    removeSnapshot(snapshot, dir);
    if (results.detectedNewAndUnableToDelete != 0) {
      myLogger.warn(message);
      if (handler != null) handler.lockedFilesDetected();
      return;
    }
    if (results.detectedDeleted > 0 || results.detectedModified > 0) {
      myLogger.warn(message);
      if (handler != null) handler.dirtyStateDetected();
      return;
    }
    myLogger.message(message, true);
    if (handler != null) handler.success();
  }

  private void removeSnapshot(File snapshot, File dir) {
    if (System.getProperty(NOT_DELETE_SNAPSHOT) != null) {
      myLogger.debug("Will not delete " + snapshot.getName()
        + " for directory " + dir.getAbsolutePath() + ", " + NOT_DELETE_SNAPSHOT + "property specified");
    } else if (!FileUtil.delete(snapshot)) {
      myLogger.warn("Unable to remove snapshot file " + snapshot.getName()
        + " for directory " + dir.getAbsolutePath());
    } else {
      myLogger.debug("Successfully removed snapshot file " + snapshot.getName()
        + " for directory " + dir.getAbsolutePath() + " after files collection");
    }
  }

  private void iterateAndCollect(File snapshot, File dir) throws Exception {
    final FilesTraversal traversal = new FilesTraversal();
    traversal.traverseCompare(new SnapshotFilesIterator(snapshot), new FileSystemFilesIterator(dir), myProcessor);
  }

  private void logUnableCollect(File snapshot, File dir, String message, Throwable e) {
    myLogger.warn("Unable to collect files in directory " + dir.getAbsolutePath()
      + " from snapshot file " + snapshot.getName() +
      ((message != null ? ", " + message : "")));
    if (e != null) {
      myLogger.exception(e);
    }
  }
}
