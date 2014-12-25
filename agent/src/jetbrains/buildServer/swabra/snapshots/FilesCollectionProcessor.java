/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.agent.impl.directories.AbstractDirectoryCleanerCallback;
import jetbrains.buildServer.agent.impl.directories.FileRemover;
import jetbrains.buildServer.swabra.Swabra;
import jetbrains.buildServer.swabra.SwabraLogger;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import jetbrains.buildServer.swabra.snapshots.iteration.FilesTraversal;
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

  private final List<File> myUnableToDeleteFiles;

  private Results myResults;

  @NotNull private final DeletionListener myDeletionListener;

  @NotNull private final AtomicBoolean myBuildInterrupted;

  @NotNull private final FileRemover myFileRemover;

  @NotNull private final String myDir;

  public FilesCollectionProcessor(@NotNull SwabraLogger logger,
                                  LockedFileResolver resolver,
                                  @NotNull File dir,
                                  boolean verbose,
                                  boolean strict,
                                  @NotNull AtomicBoolean buildInterruptedFlag) {
    myBuildInterrupted = buildInterruptedFlag;
    myLogger = logger;
    myLockedFileResolver = resolver;
    myDir = dir.getAbsolutePath();
    myVerbose = verbose;
    myStrictDeletion = strict;

    myUnableToDeleteFiles = new ArrayList<File>();

    myDeletionListener = new DeletionListener();
    myFileRemover = new FileRemover(new AbstractDirectoryCleanerCallback());
  }

  public boolean willProcess(FileInfo info) throws InterruptedException {
    if (myBuildInterrupted.get()){
      throw new InterruptedException();
    }

    return !myDir.equals(info.getPath());
  }

  public void processUnchanged(FileInfo info) {
    ++myDetectedUnchanged;
    myLogger.debug("Detected unchanged " + info.getPath());
  }

  public void processModified(FileInfo info1, FileInfo info2) {
    ++myDetectedModified;
    myLogger.warn("Detected modified " + info1.getPath());
  }

  public void processDeleted(FileInfo info) {
    ++myDetectedDeleted;
    myLogger.warn("Detected deleted " + info.getPath());
  }

  public void processAdded(FileInfo info) {
    final File file = new File(info.getPath());
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

  public void comparisonStarted() {
    myResults = null;
  }

  public void comparisonFinished() {

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
    if (myFileRemover.doDelete(f, false, true)) {
      return true;
    }

    if (f.isDirectory() && unableToDeleteDescendant(f)) {
      return false;
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
