/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.swabra.snapshots.SwabraRules;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import jetbrains.buildServer.swabra.snapshots.iteration.FileSystemFilesIterator;
import jetbrains.buildServer.swabra.snapshots.iteration.FilesTraversal;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import junit.framework.TestCase;
import org.assertj.core.api.BDDAssertions;

import static jetbrains.buildServer.swabra.TestUtil.getTestData;

/**
 * User: vbedrosova
 * Date: 30.01.2010
 * Time: 17:40:03
 */
public class FileSystemFilesTraversalTest extends TestCase {
  private void runTest(String resultsFileName, boolean fullListing, String... rulesString) throws Exception {
    final FilesTraversal traversal = new FilesTraversal();
    final StringBuffer results = new StringBuffer();

    final TempFiles tempFiles = new TempFiles();
    try {
      final File root = new File(tempFiles.createTempDir(), "root");
      FileUtil.copyDir(getTestData("filesTraverse", null), root);
      TestUtil.deleteSvnFiles(root);

      final SwabraRules rules = new SwabraRules(root, Arrays.asList(rulesString));
      traversal.traverse(new FileSystemFilesIterator(root, rules),
        new FilesTraversal.SimpleProcessor() {
          public void process(FileInfo file) {
            results.append(file.getPath()).append("\n");
          }
        });

      final File goldFile = getTestData(resultsFileName + ".gold", null);
      final String resultsFile = goldFile.getAbsolutePath().replace(".gold", ".tmp");

      final String actual = results.toString().trim().replace(root.getAbsolutePath(), "##ROOT##").replace("/", "\\");
      final String expected = FileUtil.readText(goldFile).trim();
      if (!actual.equals(expected)) {
        final FileWriter resultsWriter = new FileWriter(resultsFile);
        resultsWriter.write(actual);
        resultsWriter.close();

        assertEquals(actual, expected, actual);
      }
    } finally {
      tempFiles.cleanup();
    }
  }

  public void test_all_files() throws Exception {
    runTest("fileSystemFilesTraversal", false);
  }

  public void test_filtered() throws Exception {
    runTest("fileSystemFilesTraversal_filtered", true, "-:**/a*");
  }

  public void test_many_dirs_excluded() throws Exception {
    final TempFiles tempFiles = new TempFiles();
    final File root = tempFiles.createTempDir();
    File dirInc = new File(root, "dirInc");
    File dirExc = new File(root, "dirExc");
    assertTrue(dirInc.mkdir());
    assertTrue(dirExc.mkdir());
    final Set<String> expectedDirs = new HashSet<>();
    expectedDirs.add("dirInc");

    // this can be here in current implementation
    expectedDirs.add("dirExc");
    for (int i=0; i<260; i++) {
      String dirI = "dir" + i;
      assertTrue(new File(root, dirI).mkdir());
      File dirIncI = new File(dirInc, dirI);
      assertTrue(dirIncI.mkdir());
      expectedDirs.add(dirI);
      expectedDirs.add("dirInc/"+dirI);

      File dirExI = new File(dirExc, dirI);
      assertTrue(dirExI.mkdir());
      for (int j=0; j<260; j++){
        String dirJ = "dir" + j;
        assertTrue(new File(dirExI, dirJ).mkdir());
        assertTrue(new File(dirIncI, dirJ).mkdir());
        expectedDirs.add("dirInc/"+dirI +"/"+dirJ);
      }
    }

    final FilesTraversal traversal = new FilesTraversal();
    final SwabraRules rules = new SwabraRules(root, Arrays.asList("+:**", "-:dirExc/**"));
    traversal.traverse(new FileSystemFilesIterator(root, rules),
                       new FilesTraversal.SimpleProcessor() {
                         public void process(FileInfo file) {
                           String path = file.getPath().replaceAll(root.getPath(), "");
                           if (StringUtil.isNotEmpty(path)) {
                             path = path.substring(1);
                             assertTrue("Expected to contain " + path, expectedDirs.remove(path));
                           }
                         }
                       });

    BDDAssertions.then(expectedDirs).isEmpty();
  }

}
