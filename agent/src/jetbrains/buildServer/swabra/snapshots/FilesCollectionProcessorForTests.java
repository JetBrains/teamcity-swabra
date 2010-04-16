package jetbrains.buildServer.swabra.snapshots;

import jetbrains.buildServer.swabra.SwabraLogger;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * User: vbedrosova
 * Date: 04.02.2010
 * Time: 14:59:20
 */
public class FilesCollectionProcessorForTests extends FilesCollectionProcessor {
  private final String myLogPath;
  private BufferedWriter myWriter;

  public FilesCollectionProcessorForTests(@NotNull SwabraLogger logger,
                                          LockedFileResolver resolver,
                                          boolean verbose,
                                          boolean strict,
                                          String logPath) {
    super(logger, resolver, verbose, strict);
    myLogPath = logPath;
  }

  private void log(String message) {
    try {
      myWriter.write(message);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void processModified(FileInfo info1, FileInfo info2) {
    super.processModified(info1, info2);
    log("MODIFIED " + info1.getPath() + "\n");
  }

  @Override
  public void processDeleted(FileInfo info) {
    super.processDeleted(info);
    log("DELETED " + info.getPath() + "\n");
  }

  @Override
  public void processAdded(FileInfo info) {
    super.processAdded(info);
    log("ADDED " + info.getPath() + "\n");
  }

  @Override
  public void comparisonFinished() {
    super.comparisonFinished();
    try {
      myWriter.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void comparisonStarted() {
    super.comparisonStarted();
    try {
      myWriter = new BufferedWriter(new FileWriter(new File(myLogPath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
