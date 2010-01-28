package jetbrains.buildServer.swabra.snapshots;

import jetbrains.buildServer.swabra.SwabraLogger;
import static jetbrains.buildServer.swabra.snapshots.SnapshotUtil.*;

import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: vbedrosova
 * Date: 23.01.2010
 * Time: 14:32:04
 */
public class FilesCollector {
  public static enum CollectionResult {
    SUCCESS, FAILURE, RETRY    
  }

  private final File myCheckoutDir;
  private final File myTempDir;  
  private final LockedFileResolver myLockedFileResolver;
  private final SwabraLogger myLogger;  
  private final boolean myStrictDeletion;
  private final boolean myVerbose;

  private Map<String, FileInfo> myFiles;

//  private final List<File> myDeleted = new ArrayList<File>();
//  private final List<File> myDetectedModified = new ArrayList<File>();
//  private final List<File> myUnableToDelete = new ArrayList<File>();
  private int myDeletedNum = 0;
  private int myDetectedModifiedNum = 0;
  private int myUnableToDeleteNum = 0;

  public FilesCollector(@NotNull File checkoutDir,
                        @NotNull File tempDir,
                        @Nullable LockedFileResolver lockedFileResolver,
                        @NotNull SwabraLogger logger,
                        boolean verbose,
                        boolean strictDeletion) {
    myCheckoutDir = checkoutDir;
    myTempDir = tempDir;
    myLockedFileResolver = lockedFileResolver;
    myLogger = logger;
    myStrictDeletion = strictDeletion;
    myVerbose = verbose;
  }

  public CollectionResult collect(@NotNull String snapshotName) {
    final File snapshot = new File(myTempDir, snapshotName + FILE_SUFFIX);
    if (!snapshot.exists() || (snapshot.length() == 0)) {
      logUnableCollect(snapshot, null, "file doesn't exist");
      return CollectionResult.FAILURE;
    }
    myDeletedNum = 0;
    myDetectedModifiedNum = 0;
    myUnableToDeleteNum = 0;
    myFiles = new HashMap<String, FileInfo>();
    BufferedReader snapshotReader = null;
    try {
      snapshotReader = new BufferedReader(new FileReader(snapshot));
      final String parentDir = snapshotReader.readLine();
      String currentDir = "";
      String fileRecord = snapshotReader.readLine();
      while (fileRecord != null) {
        final String path = getFilePath(fileRecord);
        final FileInfo fi = new FileInfo(getFileLength(fileRecord), getFileLastModified(fileRecord));
        if (path.endsWith(File.separator)) {
          currentDir = parentDir + path;
          myFiles.put(currentDir.substring(0, currentDir.length() - 1), fi);
        } else {
          myFiles.put(currentDir + path, fi);
        }
        fileRecord = snapshotReader.readLine();
      }
      return collectInternal(myCheckoutDir);
    } catch (Exception e) {
      logUnableCollect(snapshot, e, "exception when reading from file");
      return CollectionResult.FAILURE;
    } finally {
      try {
        if (snapshotReader != null) {
          snapshotReader.close();
        }
        if (myUnableToDeleteNum == 0) {
          if (!FileUtil.delete(snapshot)) {
            myLogger.error("Swabra: Unable to remove snapshot file " + snapshot.getAbsolutePath()
              + " for directory " + myCheckoutDir.getAbsolutePath());
          }
        }
        myFiles.clear();
      } catch (Exception e) {
        logUnableCollect(snapshot, e, "exception when closing file");
        return CollectionResult.FAILURE;
      }
    }
  }

  private void logUnableCollect(File snapshot, Exception e, String message) {
    myLogger.error("Swabra: Unable to collect files in checkout directory " + myCheckoutDir.getAbsolutePath()
      + " from snapshot file " + snapshot.getAbsolutePath() +
      ((message != null ? ", " + message : "")));
    if (e != null) {
      myLogger.exception(e, true);
    }
  }

  private CollectionResult collectInternal(@NotNull final File dir) {
    myLogger.message("Swabra: Scanning checkout directory " + myCheckoutDir + " for newly created and modified files...", true);
    myLogger.activityStarted();
    collectRec(dir);
    myFiles.remove(myCheckoutDir.getAbsolutePath());
    for (String file : myFiles.keySet()) {
      myLogger.message("Detected deleted " + file, myVerbose);
    }
    myLogger.activityFinished();
    final String message = "Swabra: Finished scanning checkout directory " + myCheckoutDir
                          + " for newly created and modified files: "
                          + myDeletedNum + " object(s) deleted, "
                          + (myUnableToDeleteNum == 0 ? "" : "unable to delete " + myUnableToDeleteNum + " object(s), ")
                          +  myDetectedModifiedNum + " object(s) detected modified, "
                          + myFiles.size() + " object(s) detected deleted";
    if (!myFiles.isEmpty()) {
      myLogger.warn(message);
      return CollectionResult.FAILURE;
    }
    if (myUnableToDeleteNum > 0) {
      myLogger.warn(message);
      return CollectionResult.RETRY;
    }
    myLogger.message(message, true);
    return CollectionResult.SUCCESS;
  }

  private void collectRec(@NotNull final File dir) {
    final File[] files = dir.listFiles();
    if (files == null || files.length == 0) return;
    for (File file : files) {
      final FileInfo info = myFiles.get(file.getAbsolutePath());
      if (info == null) {
        if (file.isDirectory()) {
          //all directory content is supposed to be garbage
          collectRec(file);
        }
        if (!FileUtil.delete(file) && !resolve(file)) {
          ++myUnableToDeleteNum;
          myLogger.warn("Detected new, unable to delete " + file.getAbsolutePath());
        } else {
          ++myDeletedNum;
          myLogger.message("Detected new and deleted " + file.getAbsolutePath(), myVerbose);
        }
      } else if (isModified(file, info)) {
//        myLogger.message("Different file found: '" + file.getAbsolutePath() +
//          "' timestamp (stored: " + info.getLastModified() + ", actual: " + file.lastModified() +
//          "), size (stored: " + info.getLength() + ", actual: " + file.length() + ")", false);
        ++myDetectedModifiedNum;
        myLogger.message("Detected modified " + file.getAbsolutePath(), myVerbose);
        if (file.isDirectory()) {
          //directory's content is supposed to be modified
          collectRec(file);
        }
      } else {
        if (file.isDirectory()) {
          //yet know nothing about directory's content, so dig into
          collectRec(file);
        }
      }
      myFiles.remove(file.getAbsolutePath());
    }
  }

  private boolean isModified(File file, FileInfo info) {
    return (file.lastModified() != info.getLastModified()) || file.length() != info.getLength();
  }

  private boolean resolve(File f) {
    if (myLockedFileResolver != null) {
      if (myStrictDeletion) {
        return myLockedFileResolver.resolveDelete(f);
      } else {
        return myLockedFileResolver.resolve(f, false);
      }
    }
    return false;
  }
}