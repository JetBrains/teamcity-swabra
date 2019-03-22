/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
import java.util.Stack;
import java.util.function.Consumer;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.swabra.snapshots.SwabraRules;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import jetbrains.buildServer.swabra.snapshots.iteration.FileSystemFilesIterator;
import jetbrains.buildServer.swabra.snapshots.iteration.FilesTraversal;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TestFor;
import junit.framework.TestCase;

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
      expectedDirs.add("dirInc"+File.separator+dirI);

      File dirExI = new File(dirExc, dirI);
      assertTrue(dirExI.mkdir());
      for (int j=0; j<260; j++){
        String dirJ = "dir" + j;
        assertTrue(new File(dirExI, dirJ).mkdir());
        assertTrue(new File(dirIncI, dirJ).mkdir());
        expectedDirs.add("dirInc"+File.separator+dirI +File.separator+dirJ);
      }
    }

    final FilesTraversal traversal = new FilesTraversal();
    final SwabraRules rules = new SwabraRules(root, Arrays.asList("+:**", "-:dirExc/**"));
    traversal.traverse(new FileSystemFilesIterator(root, rules),
                       new FilesTraversal.SimpleProcessor() {
                         public void process(FileInfo file) {
                           String path = file.getPath().replace(root.getPath(), "");
                           if (StringUtil.isNotEmpty(path)) {
                             path = path.substring(1);
                             assertTrue("Expected to contain " + path, expectedDirs.remove(path));
                           }
                         }
                       });

    assertTrue(expectedDirs.isEmpty());
  }

  @TestFor(issues = "TW-55694")
  public void test_many_dirs_excluded2() throws Exception {
    final TempFiles tempFiles = new TempFiles();
    final File root = tempFiles.createTempDir();
    final File srcDir = new File(root, "src");
    srcDir.mkdir();
    final File svnDir = new File(srcDir, ".svn");
    svnDir.mkdir();
    final File otherDir = new File(srcDir, "other");
    otherDir.mkdir();
    for (int i=0; i<360; i++) {
      File dirIncI = new File(svnDir, "dir" + i);
      assertTrue(dirIncI.mkdir());
      File file = new File(dirIncI, "file.txt");
      file.createNewFile();
    }

    final File file1 = new File(srcDir, "srcFile.java");
    file1.createNewFile();
    final File file2 = new File(otherDir, "otherFile.java");
    file2.createNewFile();
    final Set<String> allowedPaths = new HashSet<>();
    allowedPaths.add(file1.getPath());
    allowedPaths.add(file2.getPath());

    final SwabraRules rules = new SwabraRules(root, Arrays.asList(SwabraSettings.DEFAULT_RULES));
    final FilesTraversal traversal = new FilesTraversal();
    final StringBuilder result = new StringBuilder();
    traversal.traverse(new FileSystemFilesIterator(root, rules), new FilesTraversal.SimpleProcessor() {
      @Override
      public void process(final FileInfo file) throws Exception {
        result.append(file.getPath().replace(root.getPath(), "")).append("\n");
      }
    });
    System.out.println(result.toString());
     String expected = "\n" +
                            "/src\n" +
                     "/src/srcFile.java\n" +
                     "/src/other\n" +
                     "/src/other/otherFile.java\n";
    assertEquals(expected.replace("/", File.separator), result.toString());
  }


  @TestFor(issues = "TW-58813")
  public void test_many_dirs_excluded3() throws Exception {
    final TempFiles tempFiles = new TempFiles();
    final File rootDir = tempFiles.createTempDir();
    final File skipDir = new File(rootDir, ".skip");
    skipDir.mkdir();
    for (int i=0; i<10; i++){
      File skipDirOne = new File(skipDir, "skipDirOne_"+i);
      skipDirOne.mkdir();
      createFiles(skipDirOne, "skipOne_", 1);
      for (int j=0; j<10; j++){
        File skipDirTwo = new File(skipDirOne, "skipDirTwo_"+j);
        skipDirTwo.mkdir();
        createFiles(skipDirTwo, "skipDirTwo_"+j + "_", 1);
      }
    }
    createFiles(rootDir, "myRootDirFile", 1);

    final FilesTraversal traversal = new FilesTraversal();
    final FileSystemFilesIterator filesIterator = new FileSystemFilesIterator(rootDir, new SwabraRules(rootDir, Arrays.asList("-:.skip", "-:**/.skip"))){
      @Override
      public FileInfo getNext() throws IOException {
        int length = Thread.currentThread().getStackTrace().length;
        assertTrue("stackTrace depth is less than 75", length <75);
        return super.getNext();
      }
    };
    final Stack<Consumer<FileInfo>> checks = new Stack<>();
    checks.push(new Consumer<FileInfo>() {
      @Override
      public void accept(final FileInfo info) {
        assertTrue(info.isFile());
        final File file = new File(info.getPath());
        assertEquals("myRootDirFile0", file.getName());
        assertEquals(rootDir, file.getParentFile());
      }
    }); // file

    checks.push(new Consumer<FileInfo>() {
      @Override
      public void accept(final FileInfo info) {
        assertFalse(info.isFile());
        final File file = new File(info.getPath());
        assertEquals(rootDir, file);
      }
    }); // dir
    traversal.traverse(filesIterator,
                       new FilesTraversal.SimpleProcessor() {
                         @Override
                         public void process(final FileInfo info) throws Exception {
                           checks.pop().accept(info);
                         }
                       });
  }

  private void createFiles(File dir, String namePrefix, int cnt){
    for (int i=0; i<cnt; i++){
      try {
        new File(dir, namePrefix + i).createNewFile();
      } catch (IOException e) {}
    }
  }
}
