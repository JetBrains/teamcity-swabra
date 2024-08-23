

package jetbrains.buildServer.swabra;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.swabra.snapshots.FilesCollectionProcessor;
import jetbrains.buildServer.swabra.snapshots.iteration.FileChangeType;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import jetbrains.buildServer.util.FileUtil;
import junit.framework.TestCase;
import org.junit.Test;

/**
 * User: vbedrosova
 * Date: 11.06.2010
 * Time: 20:08:55
 */
public class FilesCollectionProcessorTest extends TestCase {
  private void doTest(String resultFile, FileChangeInfo... changes) throws Exception {
    final StringBuilder sb = new StringBuilder();
    final SwabraLogger logger = new SwabraLogger();
    logger.setBuildLogger(new BuildProgressLoggerMock(sb));

    final FilesCollectionProcessor processor = new FilesCollectionProcessorForTests(logger, null, new File(""), true, true, new AtomicBoolean(false));

    processor.comparisonStarted();

    for (final FileChangeInfo changeInfo : changes) {
      switch (changeInfo.type) {
        case UNCHANGED:
          processor.processUnchanged(new FileInfo(changeInfo.path, 0, 0, changeInfo.isFile));
          break;
        case ADDED:
          processor.processAdded(new FileInfo(changeInfo.path, 0, 0, changeInfo.isFile));
          break;
        case MODIFIED:
          final FileInfo info = new FileInfo(changeInfo.path, 0, 0, changeInfo.isFile);
          processor.processModified(info, info);
          break;
        case DELETED:
          processor.processDeleted(new FileInfo(changeInfo.path, 0, 0, changeInfo.isFile));
          break;
      }
    }

    processor.comparisonFinished();

    String actualStr = sb.toString();
    System.out.println(actualStr);
    actualStr = actualStr.replace(new File("").getAbsolutePath() + File.separator, "");
    actualStr = actualStr.replace(new File("").getAbsolutePath(), "");
    actualStr = SwabraUtil.unifyPath(actualStr, '/').trim();

    final String expectedStr = FileUtil.readText(TestUtil.getTestData(resultFile + ".result", "filesCollectionProcessor")).trim();

    assertEquals("Unexpected result", expectedStr, actualStr);
  }

  private static final class FileChangeInfo {
    final FileChangeType type;
    final String path;
    final boolean isFile;

    private FileChangeInfo(FileChangeType type, String path, boolean file) {
      this.type = type;
      this.path = path;
      isFile = file;
    }
  }

  @Test
  public void test_added_dir_followed_by_deleted() throws Exception {
    doTest("added_dir_followed_by_deleted",
      new FileChangeInfo(FileChangeType.ADDED, "a", false),
      new FileChangeInfo(FileChangeType.DELETED, "d", true)
    );
  }

  @Test
  public void test_added_dir_followed_by_unchanged() throws Exception {
    doTest("added_dir_followed_by_unchanged",
      new FileChangeInfo(FileChangeType.ADDED, "a", false),
      new FileChangeInfo(FileChangeType.UNCHANGED, "d", true)
    );
  }

  @Test
  public void test_added_dir_followed_by_modified() throws Exception {
    doTest("added_dir_followed_by_modified",
      new FileChangeInfo(FileChangeType.ADDED, "a", false),
      new FileChangeInfo(FileChangeType.MODIFIED, "d", true)
    );
  }

  @Test
  public void test_interruption() throws Exception {
    FileChangeInfo[] changes = new FileChangeInfo[]{
      new FileChangeInfo(FileChangeType.ADDED, "a", false),
      new FileChangeInfo(FileChangeType.MODIFIED, "d", true)
    };

    final StringBuilder sb = new StringBuilder();
    final SwabraLogger logger = new SwabraLogger();
    logger.setBuildLogger(new BuildProgressLoggerMock(sb));

    final AtomicBoolean interruptedFlag = new AtomicBoolean(false);
    boolean wasActuallyInterrupted = false;
    int entriesProcessed = 0;

    final FilesCollectionProcessor processor = new FilesCollectionProcessorForTests(logger, null, new File(""), true, true, interruptedFlag);

    processor.comparisonStarted();
    try {
      for (final FileChangeInfo changeInfo : changes) {
        final FileInfo fileInfo = new FileInfo(changeInfo.path, 0, 0, changeInfo.isFile);
        if (processor.willProcess(fileInfo)) {
          switch (changeInfo.type) {
            case UNCHANGED:
              processor.processUnchanged(fileInfo);
              break;
            case ADDED:
              processor.processAdded(fileInfo);
              break;
            case MODIFIED:
              processor.processModified(fileInfo, fileInfo);
              break;
            case DELETED:
              processor.processDeleted(fileInfo);
              break;
          }
        }
        entriesProcessed++;
        if (!interruptedFlag.get()) {
          interruptedFlag.set(true);
        }
      }
    } catch (InterruptedException ex) {
      wasActuallyInterrupted = true;
    }

    processor.comparisonFinished();
    assertTrue(wasActuallyInterrupted);
    assertEquals(1, entriesProcessed);
  }
}