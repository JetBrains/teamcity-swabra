package jetbrains.buildServer.swabra.snapshots;

import jetbrains.buildServer.swabra.SwabraLogger;

import static jetbrains.buildServer.swabra.snapshots.SnapshotUtil.*;

import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import jetbrains.buildServer.swabra.snapshots.iteration.FileSystemFilesIterator;
import jetbrains.buildServer.swabra.snapshots.iteration.FilesTraversal;
import jetbrains.buildServer.swabra.snapshots.iteration.SnapshotFilesIterator;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * User: vbedrosova
 * Date: 23.01.2010
 * Time: 14:32:04
 */
public class FilesCollector {
  public static enum CollectionResult {
    SUCCESS, FAILURE, RETRY
  }

  private File mySnapshot;
  private final File myCheckoutDir;
  private final File myTempDir;
  private final LockedFileResolver myLockedFileResolver;
  private final SwabraLogger myLogger;
  private final boolean myStrictDeletion;
  private final boolean myVerbose;

  private int myDetectedNewAndDeleted;
  private int myDetectedModified;
  private int myDetectedDeleted;

  private File myCurrentNewDir;
  private final List<File> myUnableToDeleteFiles;

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
    myUnableToDeleteFiles = new ArrayList<File>();
  }

  public CollectionResult collect(@NotNull String snapshotName) {
    mySnapshot = new File(myTempDir, snapshotName + FILE_SUFFIX);
    if (!mySnapshot.exists() || (mySnapshot.length() == 0)) {
      logUnableCollect(mySnapshot, null, "file doesn't exist");
      return CollectionResult.FAILURE;
    }

    myDetectedNewAndDeleted = 0;
    myDetectedModified = 0;
    myDetectedDeleted = 0;
    myUnableToDeleteFiles.clear();
    myCurrentNewDir = null;

    myLogger.message("Swabra: Scanning checkout directory " + myCheckoutDir + " for newly created and modified files...", true);
    myLogger.activityStarted();


    try {
      iterateAndCollect();
    } catch (Exception e) {
      logUnableCollect(mySnapshot, e, null);
      return CollectionResult.FAILURE;
    }

    if (myCurrentNewDir != null) {
      deleteSubDirs(myCurrentNewDir);
    }

    if (myUnableToDeleteFiles.isEmpty()) {
      if (!FileUtil.delete(mySnapshot)) {
        myLogger.error("Swabra: Unable to remove snapshot file " + mySnapshot.getAbsolutePath()
          + " for directory " + myCheckoutDir.getAbsolutePath());
      }
    }
    myLogger.activityFinished();
    final String message = "Swabra: Finished scanning checkout directory " + myCheckoutDir
      + " for newly created and modified files: "
      + myDetectedNewAndDeleted + " object(s) deleted, "
      + (myUnableToDeleteFiles.isEmpty() ? "" : "unable to delete " + myUnableToDeleteFiles.size() + " object(s), ")
      + myDetectedModified + " object(s) detected modified, "
      + myDetectedDeleted + " object(s) detected deleted";
    if (myDetectedDeleted > 0) {
      myLogger.warn(message);
      return CollectionResult.FAILURE;
    }
    if (!myUnableToDeleteFiles.isEmpty()) {
      myLogger.warn(message);
      return CollectionResult.RETRY;
    }
    myLogger.message(message, true);
    return CollectionResult.SUCCESS;
  }

  private void iterateAndCollect() throws Exception {
    final FilesTraversal traversal = new FilesTraversal();
    traversal.traverseCompare(new SnapshotFilesIterator(mySnapshot),
                              new FileSystemFilesIterator(myCheckoutDir),
                              new FilesCollectorProcessor());
  }

  private void logUnableCollect(File snapshot, Exception e, String message) {
    myLogger.error("Swabra: Unable to collect files in checkout directory " + myCheckoutDir.getAbsolutePath()
      + " from snapshot file " + snapshot.getAbsolutePath() +
      ((message != null ? ", " + message : "")));
    if (e != null) {
      myLogger.exception(e, true);
    }
  }

  private void deleteSubDirs(File dir) {
    final File[] files = dir.listFiles();
    if (files != null && files.length > 0) {
      for (File f : files) {
        if (f.isDirectory()) {
          deleteSubDirs(f);
        }
      }
    }
    deleteObject(dir);
  }

  private void deleteObject(File file) {
    if (!resolveDelete(file)) {
      myUnableToDeleteFiles.add(file);
      myLogger.warn("Detected new, unable to delete " + file.getAbsolutePath());
    } else {
      ++myDetectedNewAndDeleted;
      myLogger.message("Detected new and deleted " + file.getAbsolutePath(), myVerbose);
    }
  }

  private boolean resolveDelete(File f) {
    if (!f.exists()) {
      return true;
    }
    if (f.isDirectory() && unableToDeleteDescendant(f)) {
      return false;
    }
    if (FileUtil.delete(f)) {
      return true;
    }
    if (myLockedFileResolver != null) {
      if (myStrictDeletion) {
        return myLockedFileResolver.resolveDelete(f);
      } else {
        return myLockedFileResolver.resolve(f, false);
      }
    }
    return false;
  }

  private boolean unableToDeleteDescendant(File file) {
    final String path = file.getAbsolutePath();
    for (final File f : myUnableToDeleteFiles) {
      if (f.getAbsolutePath().startsWith(path)) {
        return true;
      }
    }
    return false;
  }

  private class FilesCollectorProcessor implements FilesTraversal.ComparisonProcessor
  {
    public void processModified(FileInfo info1, FileInfo info2) {
      ++myDetectedModified;
      myLogger.message("Detected modified " + info1.getPath(), myVerbose);
    }

    public void processDeleted(FileInfo info) {
      ++myDetectedDeleted;
      myLogger.message("Detected deleted " + info.getPath(), myVerbose);
    }

    public void processAdded(FileInfo info) {
      final File file = new File(info.getPath());
      if (file.isFile()) {
        deleteObject(file);
      } else {
        if (myCurrentNewDir == null) {
          myCurrentNewDir = file;
        } else if (!file.getAbsolutePath().startsWith(myCurrentNewDir.getAbsolutePath())) {
          deleteSubDirs(myCurrentNewDir);
          myCurrentNewDir = file;
        }
      }
    }
  }
}