package jetbrains.buildServer.swabra.snapshots.iteration;

import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;

import java.io.File;
import java.util.Comparator;

/**
 * User: vbedrosova
 * Date: 02.02.2010
 * Time: 13:22:24
 */
public class FilesComparator implements Comparator<File> {
  public int compare(File o1, File o2) {
    return compare(o1.getAbsolutePath(), o1.isFile(), o2.getAbsolutePath(), o2.isFile());
  }

  public static int compare(FileInfo o1, FileInfo o2) {
    return compare(o1.getPath(), o1.isFile(), o2.getPath(), o2.isFile());
  }

  private static int compare(String path1, boolean isFile1, String path2, boolean isFile2) {
    if (isFile1) {
      if (!isFile2) {
        return -1;
      }
    } else {
      if (isFile2) {
        return 1;
      }
    }
    return path1.compareTo(path2);
  }
}