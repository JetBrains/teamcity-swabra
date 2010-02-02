package jetbrains.buildServer.swabra.snapshots;

import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;

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
    final boolean isFile = file.isFile();
    String fPath = file.getAbsolutePath();
    fPath = isFile ? file.getName() : getDirPath(baseDirName, fPath); //+1 for trailing slash
    return getSnapshoEntry(fPath, file.length(), file.lastModified());
  }

  public static String getSnapshotEntry(@NotNull FileInfo file, @NotNull String baseDirName) {
    final boolean isFile = file.isFile();
    String fPath = file.getPath();
    fPath = isFile ? fPath.substring(fPath.lastIndexOf(File.separator) + 1): getDirPath(baseDirName, fPath); //+1 for trailing slash
    return getSnapshoEntry(fPath, file.getLength(), file.getLastModified());
  }

  private static String getDirPath(String baseDirName, String fPath) {
    return fPath.substring(fPath.indexOf(baseDirName) + baseDirName.length() + 1) + File.separator;  //+1 for trailing slash
  }

  private static String getSnapshoEntry(String path, long length, long lastModified) {
    return path + SEPARATOR + length + SEPARATOR + encodeDate(lastModified) + LINE_SEPARATOR;
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
}
