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

package jetbrains.buildServer.swabra;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.Nullable;

/**
 * User: vbedrosova
 * Date: 30.01.2010
 * Time: 17:43:40
 */
class TestUtil {
  private static final String TEST_DATA_PATH = "tests" + File.separator + "testData";

  public static String getTestDataPath(@Nullable final String fileName, @Nullable final String folderName) throws Exception {
    return getTestData(fileName, folderName).getAbsolutePath();
  }

  public static File getTestData(@Nullable final String fileName, @Nullable final String folderName) throws Exception {
    final String relativeFileName = TEST_DATA_PATH + (folderName != null ? File.separator + folderName : "") + (fileName != null ? File.separator + fileName : "");
    final File file1 = new File(relativeFileName);
    if (file1.exists()) {
      return file1;
    }
    final File file2 = new File("svnrepo" + File.separator + "swabra" + File.separator + relativeFileName);
    if (file2.exists()) {
      return file2;
    }
    throw new FileNotFoundException("Either " + file1.getAbsolutePath() + " or file " + file2.getAbsolutePath() + " should exist.");
  }

  private static final String SVN_FILE = ".svn";

  public static void deleteSvnFiles(File root) {
    final List<File> subDirectories = FileUtil.getSubDirectories(root);
    for (File f : subDirectories) {
      if (SVN_FILE.equals(f.getName())) {
        FileUtil.delete(f);
      } else {
        deleteSvnFiles(f);
      }
    }
  }
}
