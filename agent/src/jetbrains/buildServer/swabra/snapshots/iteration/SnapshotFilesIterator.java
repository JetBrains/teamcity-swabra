

package jetbrains.buildServer.swabra.snapshots.iteration;

import java.io.*;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.swabra.snapshots.SnapshotUtil.*;

/**
 * User: vbedrosova
 * Date: 30.01.2010
 * Time: 16:13:55
 */
public class SnapshotFilesIterator implements FilesIterator {
  private static final Logger LOG = Logger.getLogger(SnapshotFilesIterator.class);

  @NotNull
  private final File mySnapshot;
  private BufferedReader myReader;

  private String myRootFolder;
  private String myCurrentDir;
  private String mySkipDir = null;

  public SnapshotFilesIterator(@NotNull File snapshot) {
    mySnapshot = snapshot;
  }

  @Nullable
  public FileInfo getNext() {
    try {
      if (myReader == null) {
        myReader = new BufferedReader(new InputStreamReader(new FileInputStream(mySnapshot), "UTF-8"));
        myRootFolder = myReader.readLine();
        myCurrentDir = "";
      }
      return processNextRecord();
    } catch (IOException e) {
      LOG.error("Error occurred when reading from input stream", e);
      stopIterator();
      return null;
    }
  }

  public void skipDirectory(final FileInfo dirInfo) {
    mySkipDir = dirInfo.getPath() + File.separator;
  }

  private FileInfo processNextRecord() throws IOException {
    String fileRecord;
    while ((fileRecord = myReader.readLine()) != null) {
      final String path = getFilePath(fileRecord);
      final long length = getFileLength(fileRecord);
      final long lastModified = getFileLastModified(fileRecord);

      final boolean isDirectory = path.endsWith("/") || path.endsWith("\\");
      final boolean skipCurrentDir = !myCurrentDir.isEmpty() && mySkipDir != null && hasParentOf(myCurrentDir, mySkipDir);
      if (isDirectory) {
        final String newDir = myRootFolder + path;
        if (skipCurrentDir && newDir.startsWith(mySkipDir)) {
          continue;
        }
        mySkipDir = null;
        myCurrentDir = newDir;
        return new FileInfo(myCurrentDir.substring(0, myCurrentDir.length() - 1), length, lastModified, false);
      } else {
        if (skipCurrentDir) {
          continue;
        }
        return new FileInfo(myCurrentDir + path, length, lastModified, true);
      }
    }
    myReader.close();
    return null;
  }

  public void stopIterator() {
    if (myReader == null) {
      return;
    }
    try {
      myReader.close();
    } catch (IOException e) {
      LOG.error("Error occurred when closing reader", e);
    }
  }

  private static boolean hasParentOf(String dirPath, String parentDir){
    File dir = new File(dirPath.replace("/", File.separator).replace("\\", File.separator));
    File parent = new File(parentDir.replace("/", File.separator).replace("\\", File.separator));
    while (dir != null){
      if (dir.getPath().equalsIgnoreCase(parent.getPath()))
        return true;
      dir = dir.getParentFile();
    }
    return false;
  }

  public boolean isCurrent() {
    return false;
  }
}