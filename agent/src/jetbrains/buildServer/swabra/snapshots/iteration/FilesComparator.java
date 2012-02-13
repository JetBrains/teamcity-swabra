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

package jetbrains.buildServer.swabra.snapshots.iteration;

import java.io.File;

/**
 * User: vbedrosova
 * Date: 02.02.2010
 * Time: 13:22:24
 */
class FilesComparator {
  private static String SEPARATOR_REGEX = "/".equals(File.separator) ? "/" : "\\\\";

  public static int compare(FileInfo o1, FileInfo o2) {
    return compare(o1.getPath(), o1.isFile(), o2.getPath(), o2.isFile());
  }

  private static int compare(String path1, boolean isFile1, String path2, boolean isFile2) {
    if (path1.equals(path2)) {
      return compareByType(isFile1, isFile2);
    }

    final String[] path1Parts = path1.split(SEPARATOR_REGEX);
    final String[] path2Parts = path2.split(SEPARATOR_REGEX);

    final int len1 = path1Parts.length;
    final int len2 = path2Parts.length;

    for (int i = 0; i < Math.min(len1, len2); ++i) {
      final int comparisonResult = path1Parts[i].compareTo(path2Parts[i]);
      if (comparisonResult != 0) {
        if (i == (len1 - 1) && len1 == len2) {
          final int comparisonByTypeResult = compareByType(isFile1, isFile2);
          if (comparisonByTypeResult != 0) {
            return comparisonByTypeResult;
          }
        }
        return comparisonResult;
      }
    }

    if (len1 < len2) {
      return -1;
    } else if (len2 < len1) {
      return 1;
    }
    return compareByType(isFile1, isFile2);
  }

  public static int compareByType(boolean isFile1, boolean isFile2) {
    if (isFile1) {
      if (!isFile2) {
        return -1;
      }
    } else {
      if (isFile2) {
        return 1;
      }
    }
    return 0;
  }
}