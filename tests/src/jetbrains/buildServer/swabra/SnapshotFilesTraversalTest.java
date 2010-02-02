package jetbrains.buildServer.swabra;

import jetbrains.buildServer.swabra.snapshots.FileInfo;
import jetbrains.buildServer.swabra.snapshots.FilesTraversal;
import jetbrains.buildServer.swabra.snapshots.SnapshotFilesIterator;
import junit.framework.TestCase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;

import static jetbrains.buildServer.swabra.TestUtil.getTestData;
import static jetbrains.buildServer.swabra.TestUtil.readFile;

/**
 * User: vbedrosova
 * Date: 30.01.2010
 * Time: 18:03:58
 */
public class SnapshotFilesTraversalTest extends TestCase {
  private void runTest(String resultsFileName) throws Exception {
    final FilesTraversal traversal = new FilesTraversal();
    final StringBuffer results = new StringBuffer();

    traversal.traverse(new SnapshotFilesIterator(getTestData("test.snapshot", null)),
      new FilesTraversal.Visitor() {
      public void visit(FileInfo file) {
        results.append(file.getPath()).append(" ").append(file.getLength()).append(" ").append(file.getLastModified()).append("\n");
      }
    });

    final File goldFile = getTestData(resultsFileName + ".gold", null);
    final String resultsFile = goldFile.getAbsolutePath().replace(".gold", ".tmp");

    final String actual = results.toString().replace(File.separator, "\\").trim();
    final String expected = readFile(goldFile).trim();
    if (!actual.equals(expected)) {
      final FileWriter resultsWriter = new FileWriter(resultsFile);
      resultsWriter.write(actual);
      resultsWriter.close();

      assertEquals(actual, expected, actual);
    }
  }

  public void test1() throws Exception {
    runTest("snapshotFilesTraversal");
  }  
}
