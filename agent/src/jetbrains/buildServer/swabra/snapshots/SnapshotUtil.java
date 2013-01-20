/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import jetbrains.buildServer.swabra.SwabraUtil;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 23.01.2010
 * Time: 14:42:00
 */
public class SnapshotUtil {
  private static final String SEPARATOR = "\t";
  private static final String LINE_SEPARATOR = "\r\n";

  private static String encodeDate(long timestamp) {
    return String.valueOf(timestamp);
  }

  private static long decodeDate(String encodedDate) {
    return Long.parseLong(encodedDate);
  }

  public static String getSnapshotHeader(@NotNull String baseDirName) {
    return SwabraUtil.unifyPath(baseDirName) + File.separator + LINE_SEPARATOR;
  }

  public static String getSnapshotEntry(@NotNull FileInfo file, @NotNull String baseDirName) {
    final boolean isFile = file.isFile();
    String fPath = file.getPath();
    fPath = isFile ? fPath.substring(fPath.lastIndexOf(File.separator) + 1) : getDirPath(baseDirName, fPath); //+1 for trailing slash
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
