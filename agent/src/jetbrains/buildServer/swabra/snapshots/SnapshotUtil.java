package jetbrains.buildServer.swabra.snapshots;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.text.ParseException;

/**
 * User: vbedrosova
 * Date: 23.01.2010
 * Time: 14:42:00
 */
public class SnapshotUtil {
  public static final String SEPARATOR = "\t";
  public static final String LINE_SEPARATOR = "\r\n";
  public static final String FILE_SUFFIX = ".snapshot";

  public static String encodeDate(long timestamp) {
    return String.valueOf(timestamp);
  }

  public static long decodeDate(String encodedDate) {
    return Long.parseLong(encodedDate);
  }

  public static String getSnapshotHeader(@NotNull String baseDirName) {
    return baseDirName + File.separator + LINE_SEPARATOR;
  }

  public static String getSnapshotEntry(@NotNull File file, @NotNull String baseDirName) {
    final boolean isDir = file.isDirectory();
    String fPath = file.getAbsolutePath();
    fPath = isDir ? fPath.substring(fPath.indexOf(baseDirName) + baseDirName.length() + 1) + File.separator : file.getName(); //+1 for trailing slash
    return fPath + SEPARATOR + file.length() + SEPARATOR + encodeDate(file.lastModified()) + LINE_SEPARATOR;
  }

  public static String getFilePath(@NotNull String snapshotEntry) {
    return snapshotEntry.substring(0, snapshotEntry.indexOf(SEPARATOR));
  }

  public static long getFileLength(@NotNull String snapshotEntry) {
    final int firstSeparator = snapshotEntry.indexOf(SEPARATOR);
    final int secondSeparator = snapshotEntry.indexOf(SEPARATOR, firstSeparator + 1);

    return Long.parseLong(snapshotEntry.substring(firstSeparator + 1, secondSeparator));
  }

  public static long getFileLastModified(@NotNull String snapshotEntry) {
    final int secondSeparator = snapshotEntry.indexOf(SEPARATOR, snapshotEntry.indexOf(SEPARATOR) + 1);

    return decodeDate(snapshotEntry.substring(secondSeparator + 1));
  }

  public static final class FileInfo {
    private final long myLength;
    private final long myLastModified;

    public FileInfo(long length, long lastModified) {
      myLength = length;
      myLastModified = lastModified;
    }

    public long getLastModified() {
      return myLastModified;
    }

    public long getLength() {
      return myLength;
    }
  }
}
