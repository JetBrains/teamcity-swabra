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

package jetbrains.buildServer.swabra.snapshots;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import jetbrains.buildServer.swabra.Swabra;
import jetbrains.buildServer.swabra.SwabraLogger;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import jetbrains.buildServer.swabra.snapshots.iteration.FilesTraversal;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 04.02.2010
 * Time: 14:35:09
 */
public class FilesCollectionProcessor implements FilesTraversal.ComparisonProcessor {
  @NotNull
  protected final SwabraLogger myLogger;
  private final LockedFileResolver myLockedFileResolver;

  private final boolean myStrictDeletion;
  private final boolean myVerbose;

  private int myDetectedUnchanged;

  private int myDetectedNewAndDeleted;
  private int myDetectedModified;
  private int myDetectedDeleted;

  private Stack<String> myAddedDirs;
  private final List<File> myUnableToDeleteFiles;

  private Results myResults;

  @NotNull
  private final DeletionListener myDeletionListener;

  @NotNull private final String myDir;

  public FilesCollectionProcessor(@NotNull SwabraLogger logger,
                                  LockedFileResolver resolver,
                                  @NotNull File dir,
                                  boolean verbose,
                                  boolean strict) {
    myLogger = logger;
    myLockedFileResolver = resolver;
    myDir = dir.getAbsolutePath();
    myVerbose = verbose;
    myStrictDeletion = strict;

    myAddedDirs = new Stack<String>();
    myUnableToDeleteFiles = new ArrayList<File>();

    myDeletionListener = new DeletionListener();
  }

  public boolean willProcess(FileInfo info) {
    return !myDir.equals(info.getPath());
  }

  public void processUnchanged(FileInfo info) {
    deleteAddedDirs();
    ++myDetectedUnchanged;
    myLogger.debug("Detected unchanged " + info.getPath());
  }

  public void processModified(FileInfo info1, FileInfo info2) {
    deleteAddedDirs();
    ++myDetectedModified;
    myLogger.warn("Detected modified " + info1.getPath());
  }

  public void processDeleted(FileInfo info) {
    deleteAddedDirs();
    ++myDetectedDeleted;
    myLogger.warn("Detected deleted " + info.getPath());
  }

  public void processAdded(FileInfo info) {
    deleteAddedDirs(info.getPath());

    final File file = new File(info.getPath());
    if (file.isFile()) {
      deleteObject(file);
    } else {
      myAddedDirs.push(info.getPath());
    }
  }

  public void comparisonStarted() {
    myResults = null;
  }

  public void comparisonFinished() {
    deleteAddedDirs();

    myResults = new Results(myDetectedUnchanged,
      myDetectedNewAndDeleted, myUnableToDeleteFiles.size(),
      myDetectedModified, myDetectedDeleted);

    myDetectedUnchanged = 0;
    myDetectedNewAndDeleted = 0;
    myDetectedModified = 0;
    myDetectedDeleted = 0;
    myUnableToDeleteFiles.clear();
  }

  public Results getResults() {
    return myResults;
  }

  private void deleteAddedDirs(String nextAdded) {
    while (canDelete(nextAdded)) {
      deleteObject(new File(myAddedDirs.pop()));
    }
  }

  private void deleteAddedDirs() {
    while (myAddedDirs.size() > 0) {
      deleteObject(new File(myAddedDirs.pop()));
    }
  }

  private boolean canDelete(String path) {
    return myAddedDirs.size() > 0 && !path.startsWith(myAddedDirs.peek());
  }

  protected void deleteObject(File file) {
    if (!resolveDelete(file)) {
      myUnableToDeleteFiles.add(file);
      myLogger.warn("Detected new, unable to delete " + file.getAbsolutePath());
    } else {
      ++myDetectedNewAndDeleted;

      final String message = "Detected new and deleted " + file.getAbsolutePath();
      if (myVerbose) {
        myLogger.message(message, true);
      } else if ("true".equalsIgnoreCase(System.getProperty(Swabra.DEBUG_MODE))) {
        myLogger.message(message, false);
      } else {
        myLogger.debug(message);
      }
    }
  }

  private final class DeletionListener implements LockedFileResolver.Listener {
    public void message(String m) {
      myLogger.message(m, true);
    }

    public void warning(String w) {
      myLogger.warn(w);
    }
  }

  @NotNull
  protected String getDir() {
    return myDir;
  }

  protected boolean resolveDelete(File f) {
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
        return myLockedFileResolver.resolveDelete(f, myDeletionListener);
      } else {
        return myLockedFileResolver.resolve(f, false, myDeletionListener);
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

  public static final class Results {
    public final int detectedUnchanged;
    public final int detectedNewAndDeleted;
    public final int detectedNewAndUnableToDelete;
    public final int detectedModified;
    public final int detectedDeleted;

    public Results(int detectedUnchanged,
                   int detectedNewAndDeleted,
                   int detectedNewAndUnableToDelete,
                   int detectedModified,
                   int detectedDeleted) {
      this.detectedUnchanged = detectedUnchanged;
      this.detectedNewAndDeleted = detectedNewAndDeleted;
      this.detectedNewAndUnableToDelete = detectedNewAndUnableToDelete;
      this.detectedModified = detectedModified;
      this.detectedDeleted = detectedDeleted;
    }
  }
}
