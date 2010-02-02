package jetbrains.buildServer.swabra.snapshots.iteration;

import org.jetbrains.annotations.NotNull;

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
    void processModified(FileInfo info1, FileInfo info2);
    void processDeleted(FileInfo info);
    void processAdded(FileInfo info);
  }

  public void traverse(@NotNull FilesIterator it, @NotNull SimpleProcessor simpleProcessor) throws Exception {
    FileInfo file = it.getNext();
    while (file != null) {
      simpleProcessor.process(file);
      file = it.getNext();
    }
  }

  public void traverseCompare(@NotNull FilesIterator it1,
                              @NotNull FilesIterator it2,
                              @NotNull ComparisonProcessor processor) throws Exception {
    processor.comparisonStarted();

    FileInfo info1 = it1.getNext();
    FileInfo info2 = it2.getNext();

    while (info1 != null && info2 != null) {
      final int comparisonResult = FilesComparator.compare(info1, info2);

      if (fileAdded(comparisonResult)) {
        processor.processAdded(info2);
        info2 = it2.getNext();
      } else if (fileDeleted(comparisonResult)) {
        processor.processDeleted(info1);
        info1 = it1.getNext();
      } else {
        if (fileModified(info1, info2)) {
          processor.processModified(info1, info2);
        }
        info1 = it1.getNext();
        info2 = it2.getNext();
      }
    }
    while (info1 != null) {
      processor.processDeleted(info1);
      info1 = it1.getNext();
    }
    while (info2 != null) {
      processor.processAdded(info2);
      info2 = it2.getNext();
    }    
    processor.comparisonFinished();
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
}
