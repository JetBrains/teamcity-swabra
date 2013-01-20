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

package jetbrains.buildServer.swabra;

import java.io.File;
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

    final FilesCollectionProcessor processor = new FilesCollectionProcessorForTests(logger, null, new File(""), true, true);

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
  public void test_added_dir_with_content() throws Exception {
    doTest("added_dir_with_content",
      new FileChangeInfo(FileChangeType.ADDED, "a", false),
      new FileChangeInfo(FileChangeType.ADDED, "a/b", false),
      new FileChangeInfo(FileChangeType.ADDED, "a/b/c", false),
      new FileChangeInfo(FileChangeType.ADDED, "a/b/c/d", true)
    );
  }

  @Test
  public void test_added__several_dirs_with_content() throws Exception {
    doTest("added_several_dirs_with_content",
      new FileChangeInfo(FileChangeType.ADDED, "a", false),
      new FileChangeInfo(FileChangeType.ADDED, "a/b", false),
      new FileChangeInfo(FileChangeType.ADDED, "a/b/c", false),
      new FileChangeInfo(FileChangeType.ADDED, "a/b/c/d", true),
      new FileChangeInfo(FileChangeType.ADDED, "a/b/e", false),
      new FileChangeInfo(FileChangeType.ADDED, "a/b/e/f", true)
    );
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
}
