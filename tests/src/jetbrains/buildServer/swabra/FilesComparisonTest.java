package jetbrains.buildServer.swabra;

import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import jetbrains.buildServer.swabra.snapshots.iteration.FilesTraversal;
import jetbrains.buildServer.swabra.snapshots.iteration.SnapshotFilesIterator;
import junit.framework.TestCase;

import java.io.File;
import java.io.FileWriter;

import static jetbrains.buildServer.swabra.TestUtil.getTestData;
import static jetbrains.buildServer.swabra.TestUtil.readFile;

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

      new SnapshotFilesIterator(getTestData(testData + "After.snapshot", null)),

      new FilesTraversal.ComparisonProcessor() {

        public void comparisonStarted() {
        }

        public void comparisonFinished() {
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
    final String expected = readFile(goldFile).trim();
    if (!actual.equals(expected)) {
      final FileWriter resultsWriter = new FileWriter(resultsFile);
      resultsWriter.write(actual);
      resultsWriter.close();

      assertEquals(actual, expected, actual);
    }
  }

  @org.junit.Test
  public void test1() throws Exception {
    runTest("filesCompare1");
  }

  @org.junit.Test
  public void test2() throws Exception {
    runTest("filesCompare2");
  }
}
