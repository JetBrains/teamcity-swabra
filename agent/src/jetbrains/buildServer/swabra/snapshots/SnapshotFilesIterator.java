package jetbrains.buildServer.swabra.snapshots;

import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

import static jetbrains.buildServer.swabra.snapshots.SnapshotUtil.getFileLastModified;
import static jetbrains.buildServer.swabra.snapshots.SnapshotUtil.getFileLength;
import static jetbrains.buildServer.swabra.snapshots.SnapshotUtil.getFilePath;

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

  public SnapshotFilesIterator(@NotNull File snapshot) {
    mySnapshot = snapshot;
  }

  @Nullable
  public FileInfo getNext() {
    try {
      if (myReader == null) {
        myReader = new BufferedReader(new FileReader(mySnapshot));
        myRootFolder = myReader.readLine();
        myCurrentDir = "";
      }
      return processNextRecord();
    } catch (IOException e) {
      LOG.error("Error occurred when reading from input stream", e);
      closeReader();
      return null;
    }
  }

  private FileInfo processNextRecord() throws IOException {
    String fileRecord = myReader.readLine();
    if (fileRecord != null) {
      final String path = getFilePath(fileRecord);
      final long length = getFileLength(fileRecord);
      final long lastModified = getFileLastModified(fileRecord);

      if (path.endsWith(File.separator)) {
        myCurrentDir = myRootFolder + path;
        return new FileInfo(myCurrentDir.substring(0, myCurrentDir.length() - 1), length, lastModified, false);
      } else {
        return new FileInfo(myCurrentDir + path, length, lastModified, true);
      }
    }
    myReader.close();
    return null;
  }

  private void closeReader() {
    try {
      myReader.close();
    } catch (IOException e) {
      LOG.error("Error occurred when closing reader", e);
    }
  }
}
