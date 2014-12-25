/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: vbedrosova
 * Date: 30.01.2010
 * Time: 17:22:24
 */
public class FilesTraversal {
  public static interface SimpleProcessor {
    void process(FileInfo file) throws Exception;
  }

  public static interface ComparisonProcessor {
    void comparisonStarted();

    void comparisonFinished();

    boolean willProcess(FileInfo info) throws InterruptedException;

    void processModified(FileInfo info1, FileInfo info2);

    void processDeleted(FileInfo info);

    void processAdded(FileInfo info);

    void processUnchanged(FileInfo info);
  }

  public void traverse(@NotNull FilesIterator it, @NotNull SimpleProcessor simpleProcessor) throws Exception {
    FileInfo file = it.getNext();
    while (file != null) {
      simpleProcessor.process(file);
      file = it.getNext();
    }
  }

  public void traverseCompare(@NotNull FilesIterator snapshotIterator,
                              @NotNull FilesIterator currentIterator,
                              @NotNull ComparisonProcessor processor) throws Exception {
    assert !snapshotIterator.isCurrent() && currentIterator.isCurrent();
    try {
      processor.comparisonStarted();

      FileInfo snapshotInfo = snapshotIterator.getNext();
      FileInfo currentInfo = currentIterator.getNext();

      while (snapshotInfo != null && currentInfo != null) {
        final int comparisonResult = FilesComparator.compare(snapshotInfo, currentInfo);
        if (fileAdded(comparisonResult)) {
          processAdded(currentInfo, processor, currentIterator);
          currentInfo = currentIterator.getNext();
        } else if (fileDeleted(comparisonResult)) {
          processDeleted(snapshotInfo, processor, snapshotIterator);
          snapshotInfo = snapshotIterator.getNext();
        } else {
          if (fileModified(snapshotInfo, currentInfo)) {
            processModified(snapshotInfo, currentInfo, processor);
          } else {
            processUnchanged(snapshotInfo, processor);
          }
          snapshotInfo = snapshotIterator.getNext();
          currentInfo = currentIterator.getNext();
        }
      }
      while (snapshotInfo != null) {
        processDeleted(snapshotInfo, processor, snapshotIterator);
        snapshotInfo = snapshotIterator.getNext();
      }
      while (currentInfo != null) {
        processAdded(currentInfo, processor, currentIterator);
        currentInfo = currentIterator.getNext();
      }
      processor.comparisonFinished();
    }
    finally {
      snapshotIterator.stopIterator();
      currentIterator.stopIterator();
    }
  }

  private static boolean fileAdded(int comparisonResult) {
    return comparisonResult > 0;
  }

  private static boolean fileDeleted(int comparisonResult) {
    return comparisonResult < 0;
  }

  private static boolean fileModified(FileInfo was, FileInfo is) {
    return (was.isFile() || is.isFile())
      && (was.getLength() != is.getLength() || was.getLastModified() != is.getLastModified());
  }

  private static void processAdded(@NotNull final FileInfo currentInfo,
                                   @NotNull final ComparisonProcessor processor,
                                   @NotNull final FilesIterator currentIterator) throws InterruptedException {
    if (processor.willProcess(currentInfo)) {
      processor.processAdded(currentInfo);
      if (!currentInfo.isFile()) {
        currentIterator.skipDirectory(currentInfo);
      }
    }
  }

  private static void processDeleted(@NotNull final FileInfo snapshotInfo,
                                     @NotNull final ComparisonProcessor processor,
                                     @NotNull final FilesIterator snapshotIterator) throws InterruptedException {
    if (processor.willProcess(snapshotInfo)) {
      processor.processDeleted(snapshotInfo);
      if (!snapshotInfo.isFile()) {
        snapshotIterator.skipDirectory(snapshotInfo);
      }
    }
  }

  private static void processModified(@NotNull final FileInfo snapshotInfo,
                                      @NotNull final FileInfo currentInfo,
                                      @NotNull final ComparisonProcessor processor){
    processor.processModified(snapshotInfo, currentInfo);
  }

  private static void processUnchanged(@NotNull final FileInfo snapshotInfo, @NotNull final ComparisonProcessor processor){
    processor.processUnchanged(snapshotInfo);
  }
}
