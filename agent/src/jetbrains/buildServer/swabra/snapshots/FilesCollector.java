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
  private final File myCheckoutDir;
  private final File myTempDir;  
  private final LockedFileResolver myLockedFileResolver;
  private final SwabraLogger myLogger;  
  private final boolean myStrictDeletion;
  private final boolean myVerbose;

  private Map<String, FileInfo> myFiles;

  private final List<File> myDeleted = new ArrayList<File>();
  private final List<File> myDetectedModified = new ArrayList<File>();
  private final List<File> myUnableToDelete = new ArrayList<File>();

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

  public boolean collect(@NotNull String snapshotName) {
    final File snapshot = new File(myTempDir, snapshotName + FILE_SUFFIX);
    if (!snapshot.exists() || (snapshot.length() == 0)) {
      logUnableCollect(snapshot, null, "file doesn't exist");
      return false;
    }
    myFiles = new HashMap<String, FileInfo>();
    BufferedReader snapshotReader = null;
    try {
      snapshotReader = new BufferedReader(new FileReader(snapshot));
      final String parentDir = snapshotReader.readLine();
      String currentDir = "";
      String fileRecord = snapshotReader.readLine();
      while (fileRecord != null) {
        final int firstSeparator = fileRecord.indexOf(SEPARATOR);
        final int secondSeparator = fileRecord.indexOf(SEPARATOR, firstSeparator + 1);
        final String path = fileRecord.substring(0, firstSeparator);
        final long length = Long.parseLong(fileRecord.substring(firstSeparator + 1, secondSeparator));
        final long lastModified = decodeDate(fileRecord.substring(secondSeparator + 1));
        final FileInfo fi = new FileInfo(length, lastModified);
        if (path.endsWith(File.separator)) {
          currentDir = parentDir + path;
          myFiles.put(currentDir.substring(0, currentDir.length() - 1), fi);
        } else {
          myFiles.put(currentDir + path, fi);
        }
        fileRecord = snapshotReader.readLine();
      }
      return collectFiles(myCheckoutDir);
    } catch (Exception e) {
      logUnableCollect(snapshot, e, "exception when reading from file");
      return false;
    } finally {
      try {
        if (snapshotReader != null) {
          snapshotReader.close();
        }
        if (myUnableToDelete.isEmpty()) {
          if (!FileUtil.delete(snapshot)) {
            myLogger.error("Swabra: Unable to remove snapshot file " + snapshot.getAbsolutePath()
              + " for directory " + myCheckoutDir.getAbsolutePath());
          }
        } else {
          myUnableToDelete.clear();
        }
        myDeleted.clear();
        myDetectedModified.clear();
        myFiles.clear();
      } catch (Exception e) {
        logUnableCollect(snapshot, e, "exception when closing file");
        return false;
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

  private boolean collectFiles(@NotNull final File dir) {
    myLogger.message("Swabra: Scanning checkout directory " + myCheckoutDir + " for newly created and modified files...", true);
    myLogger.activityStarted();
    collectInt(dir);
    logTotals();
    myLogger.activityFinished();
    final String message = "Swabra: Finished scanning checkout directory " + myCheckoutDir
                          + " for newly created and modified files: "
                          + myDeleted.size() + " object(s) deleted, "
                          + (myUnableToDelete.isEmpty() ? "" : "unable to delete " + myUnableToDelete.size() + " object(s), ")
                          +  myDetectedModified.size() + " object(s) detected modified";
    if (myUnableToDelete.isEmpty()) {
      myLogger.message(message, true);
      return true;
    } else {
      myLogger.warn(message);
      return false;
    }
  }

  private void collectInt(@NotNull final File dir) {
    final File[] files = dir.listFiles();
    if (files == null || files.length == 0) return;
    for (File file : files) {
      final FileInfo info = myFiles.get(file.getAbsolutePath());
      if (info == null) {
        if (file.isDirectory()) {
          //all directory content is supposed to be garbage
          collectInt(file);
        }
        if (!FileUtil.delete(file) && (unableToDeleteDescendant(file.getAbsolutePath()) || !resolve(file))) {
          myUnableToDelete.add(file);
        } else {
          myDeleted.add(file);
        }
      } else if (isModified(file, info)) {
        myLogger.message("Different file found: '" + file.getAbsolutePath() +
          "' timestamp (stored: " + info.getLastModified() + ", actual: " + file.lastModified() +
          "), size (stored: " + info.getLength() + ", actual: " + file.length() + ")", false);
        myDetectedModified.add(file);
        if (file.isDirectory()) {
          //directory's content is supposed to be modified
          collectInt(file);
        }
      } else {
        if (file.isDirectory()) {
          //yet know nothing about directory's content, so dig into
          collectInt(file);
        }
      }
    }
  }

  private boolean isModified(File file, FileInfo info) {
    return (file.lastModified() != info.getLastModified()) || file.length() != info.getLength();
  }

  private void logTotals() {
    if (!myDeleted.isEmpty()) {
      for (File file : myDeleted) {
        myLogger.message("Deleting " + file.getAbsolutePath(), myVerbose);
      }
    }
    if (!myUnableToDelete.isEmpty()) {
      for (File file : myUnableToDelete) {
        myLogger.warn("Unable to delete " + file.getAbsolutePath());
      }
    }
    if (!myDetectedModified.isEmpty()) {
      for (File file : myDetectedModified) {
        myLogger.message("Detected modified " + file.getAbsolutePath(), myVerbose);
      }
    }
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

  private boolean unableToDeleteDescendant(String path) {
    for (final File f : myUnableToDelete) {
      if (f.getAbsolutePath().startsWith(path)) {
        return true;
      }
    }
    return false;
  }

  private static final class FileInfo {
    private final long myLength;
    private final long myLastModified;

    public FileInfo(long length, long lastModified) {
      myLength = length;
      myLastModified = lastModified;
    }

    public long getLastModified() {
      return myLastModified;
    }

    public long getLength() {
      return myLength;
    }
  }
}