package jetbrains.buildServer.swabra;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.FileWriter;

/**
 * User: vbedrosova
 * Date: 01.06.2009
 * Time: 17:09:05
 */
public class FileDirectorySnapshot implements DirectorySnapshot {
  private final File myWorkingDir;
  private FileWriter mySnapshotWriter;

  public FileDirectorySnapshot(File workingDir) {
    myWorkingDir = workingDir;
  }

  public void snapshot(@NotNull File dir, @NotNull SwabraLogger logger, boolean verbose) {
    try {
      mySnapshotWriter = new FileWriter(new File(myWorkingDir, dir.getName() + ".snapshot"));
    } catch (IOException e) {
      logger.log("Unable to save working directory snapshot to file", false);
    }
    saveState(dir);
  }

  public void collectGarbage(File dir, @NotNull SwabraLogger logger, boolean verbose) {
    final File snapshot = new File(myWorkingDir, dir.getName() + ".snapshot");
    snapshot.delete();
  }

  private void saveState(@NotNull final File dir) {
    final File[] files = dir.listFiles();
    if (files == null || files.length == 0) return;
    for (File file : files) {
      //mySnapshotWriter.write();
      if (file.isDirectory()) {
        saveState(file);
      }
    }
  }  
}
