/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfo;
import java.io.*;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import jetbrains.buildServer.swabra.snapshots.iteration.FilesTraversal;
import jetbrains.buildServer.swabra.snapshots.iteration.SnapshotFilesIterator;
import jetbrains.buildServer.util.FileUtil;
import junit.framework.TestCase;
import org.junit.Assume;

import static jetbrains.buildServer.swabra.TestUtil.getTestData;

/**
 * User: vbedrosova
 * Date: 16.04.2010
 * Time: 14:49:15
 */
public class FilesComparisonTest extends TestCase {
  private void runTest(String testData) throws Exception {
    final FilesTraversal traversal = new FilesTraversal();
    final StringBuffer results = new StringBuffer();

    traversal.traverseCompare(

      new SnapshotFilesIterator(getTestData(testData + "Before.snapshot", null)),

      new SnapshotFilesIterator(getTestData(testData + "After.snapshot", null)){
        @Override
        public boolean isCurrent() {
          return true;
        }
      },

      new FilesTraversal.ComparisonProcessor() {

        public void comparisonStarted() {
        }

        public void comparisonFinished() {
        }

        public boolean willProcess(FileInfo info) {
          return true;
        }

        public void processModified(FileInfo info1, FileInfo info2) {
          results.append("MODIFIED: ").append(info1.getPath()).append("\n");
        }

        public void processDeleted(FileInfo info) {
          results.append("DELETED: ").append(info.getPath()).append("\n");
        }

        public void processAdded(FileInfo info) {
          results.append("ADDED: ").append(info.getPath()).append("\n");
        }

        public void processUnchanged(FileInfo info) {
          results.append("UNCHANGED: ").append(info.getPath()).append("\n");
        }
      });

    final File goldFile = getTestData(testData + ".gold", null);
    final String resultsFile = goldFile.getAbsolutePath().replace(".gold", ".tmp");

    final String actual = results.toString().trim().replace("/", "\\");
    final String expected = FileUtil.readText(goldFile, "UTF-8").trim();
    if (!actual.equals(expected)) {
      final Writer resultsWriter = new OutputStreamWriter(new FileOutputStream(resultsFile), "UTF-8");
      resultsWriter.write(actual);
      resultsWriter.close();

      assertEquals(actual.replaceAll("\r\n", "\n"), expected.replaceAll("\r\n", "\n"), actual.replaceAll("\r\n", "\n"));
    }
  }

  @org.junit.Test
  public void test1() throws Exception {
    Assume.assumeTrue("For windows only", SystemInfo.isWindows);
    runTest("filesCompare1");
  }

  @org.junit.Test
  public void test2() throws Exception {
    runTest("filesCompare2");
  }

  // TW-29332
  @org.junit.Test
  public void testUnicodeFileNames() throws Exception {
    runTest("unicodeFileNames");
  }

  @org.junit.Test
  public void test_skip_dir() throws Exception {
    runTest("compare/compareSkipDirs");
  }

  @org.junit.Test
  public void test_skip_dir_first_in_list() throws Exception {
    runTest("compare/compareSkipDirsFirstInList");
  }

  @org.junit.Test
  public void test_skip_dir_last_in_list() throws Exception {
    runTest("compare/compareSkipDirsLastInList");
  }
}
