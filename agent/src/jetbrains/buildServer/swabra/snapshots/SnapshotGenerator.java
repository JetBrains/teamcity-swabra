package jetbrains.buildServer.swabra.snapshots;

import jetbrains.buildServer.swabra.SwabraLogger;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static jetbrains.buildServer.swabra.snapshots.SnapshotUtil.*;

/**
 * User: vbedrosova
 * Date: 23.01.2010
 * Time: 14:04:16
 */
public class SnapshotGenerator {
  private final File myTempDir;
  private final File myCheckoutDir;
  private String myCheckoutDirParent;

  private final SwabraLogger myLogger;

  public SnapshotGenerator(@NotNull File checkoutDir,
                           @NotNull File tempDir,
                           @NotNull SwabraLogger logger) {
    myTempDir = tempDir;
    myCheckoutDir = checkoutDir;
    myCheckoutDirParent = checkoutDir.getParent();
    if (myCheckoutDirParent.endsWith(File.separator)) {
      myCheckoutDirParent = myCheckoutDirParent.substring(0, myCheckoutDirParent.length() - 1);
    }
    myLogger = logger;
  }

  public boolean generateSnapshot(@NotNull String snapshotName) {
    final File snapshot = new File(myTempDir, snapshotName + FILE_SUFFIX);
    if (snapshot.exists()) {
      myLogger.debug("Swabra: Snapshot file " + snapshot.getAbsolutePath() + " exists, try deleting");
      if (!FileUtil.delete(snapshot)) {
        myLogger.debug("Swabra: Unable to delete " + snapshot.getAbsolutePath());
        return false;
      }
    }
    myLogger.message("Swabra: Saving state of checkout directory " + myCheckoutDir +
      " to snapshot file " + snapshot.getAbsolutePath(), true);

    BufferedWriter writer = null;
    try {
      writer = new BufferedWriter(new FileWriter(snapshot));
      writer.write(getSnapshotHeader(myCheckoutDirParent));

      iterateAndBuildSnapshot(writer);

      myLogger.message("Swabra: Finished saving state of checkout directory " + myCheckoutDir + " to snapshot file " + snapshot.getAbsolutePath(), false);
    } catch (Exception e) {
      myLogger.error("Swabra: Unable to save snapshot of checkout directory '" + myCheckoutDir.getAbsolutePath()
        + "' to file " + snapshot.getAbsolutePath());
      myLogger.exception(e, true);
      return false;
    } finally {
      try {
        if (writer != null) {
          writer.close();
        }
      } catch (IOException e) {
        myLogger.exception(e, true);
        return false;
      }
    }
    return true;
  }

  private void iterateAndBuildSnapshot(final BufferedWriter writer) throws Exception {
    final FilesTraversal tr = new FilesTraversal();
    tr.traverse(new FileSystemFilesIterator(myCheckoutDir), new FilesTraversal.Visitor() {
      public void visit(FileInfo file) throws Exception {
        writer.write(getSnapshotEntry(file, myCheckoutDirParent));
      }
    });
  }
}
