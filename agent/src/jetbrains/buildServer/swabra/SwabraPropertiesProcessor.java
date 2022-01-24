/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.swabra;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.impl.directories.DirectoryMapAction;
import jetbrains.buildServer.agent.impl.directories.DirectoryMapItem;
import jetbrains.buildServer.agent.impl.directories.DirectoryMapPersistance;
import jetbrains.buildServer.agent.impl.directories.DirectoryMapStructure;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.swabra.SwabraUtil.unifyPath;

/**
 * User: vbedrosova
 * Date: 18.11.2009
 * Time: 16:50:49
 */

public class SwabraPropertiesProcessor extends AgentLifeCycleAdapter {
  static final String FILE_NAME = "snapshot.map";
  private static final String KEY_VAL_SEPARATOR = "=";

  private static final String SNAPSHOT_SUFFIX = ".snapshot";

  private final Map<String, DirInfo> myProperties;
  private final SwabraLogger myLogger;
  @NotNull private final DirectoryMapPersistance myPersist;
  private File myPropertiesFile;

  private CountDownLatch myCleanupFinishedSignal;

  public SwabraPropertiesProcessor(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher,
                                   @NotNull final SwabraLogger logger,
                                   @NotNull final DirectoryMapPersistance persistance) {
    myPersist = persistance;
    agentDispatcher.addListener(this);
    myLogger = logger;
    myProperties = new HashMap<String, DirInfo>();
  }

  @Override
  public void afterAgentConfigurationLoaded(@NotNull BuildAgent agent) {
    myPropertiesFile = new File(agent.getConfiguration().getCacheDirectory(Swabra.CACHE_KEY), FILE_NAME);
  }

  @Override
  public void agentStarted(@NotNull BuildAgent agent) {
    myCleanupFinishedSignal = new CountDownLatch(1);

    final List<File> actualCheckoutDirs = new ArrayList<File>();
    myPersist.withDirectoriesMap(new DirectoryMapAction() {
      public void action(@NotNull final DirectoryMapStructure data) {
        final List<DirectoryMapItem> allItems = data.getAllItems();
        for (DirectoryMapItem item : allItems) {
          actualCheckoutDirs.add(item.getLocation());
        }
      }
    });
    new Thread(new Runnable() {
      public void run() {
        cleanupPropertiesAndSnapshots(actualCheckoutDirs);
      }
    }, "Swabra-cleanup-snapshots").start();
  }

  @Override
  public void checkoutDirectoryRemoved(@NotNull File checkoutDir) {
    deleteRecord(checkoutDir);
    FileUtil.delete(getSnapshotFile(checkoutDir));
  }

  public synchronized void deleteRecord(@NotNull File dir) {
    readProperties(false);
    myProperties.remove(unifyPath(dir));
    writeProperties();
  }

  public synchronized void deleteRecords(@NotNull File checkoutDir) {
    readProperties(false);

    final ArrayList<String> propertiesToRemove = new ArrayList<String>();
    final ArrayList<File> snapshotsToRemove = new ArrayList<File>();

    final String unifiedCheckoutDirPath = unifyPath(checkoutDir);
    for (Map.Entry<String, DirInfo> entry : myProperties.entrySet()) {
      final DirInfo info = entry.getValue();
      final String monitoredDir = entry.getKey();
      if (info.checkoutDir.equals(unifiedCheckoutDirPath)) {
        propertiesToRemove.add(unifyPath(monitoredDir));
        snapshotsToRemove.add(getSnapshotFile(new File(monitoredDir)));
      }
    }
    for (String s : propertiesToRemove) {
      myProperties.remove(s);
    }

    writeProperties();

    for (File f : snapshotsToRemove) {
      FileUtil.delete(f);
    }
  }

  private void readProperties(boolean preserveFile) {
    if (myCleanupFinishedSignal != null) {
      try {
        myCleanupFinishedSignal.await();
      } catch (InterruptedException e) {
        myLogger.warn("Thread interrupted");
      }
    }
    readPropertiesNoAwait(preserveFile);
  }

  private void readPropertiesNoAwait(boolean preserveFile) {
    if (!myPropertiesFile.isFile()) {
      myLogger.debug("Couldn't read directories states from " + myPropertiesFile.getAbsolutePath() + ", no file present");
      return;
    }
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(myPropertiesFile));
      String fileRecord = reader.readLine();
      while (fileRecord != null) {
        final String[] mapElem = fileRecord.split(KEY_VAL_SEPARATOR);
        if (mapElem.length > 4 || mapElem.length < 2) {
          myLogger.warn("Error reading directories states from " + myPropertiesFile.getAbsolutePath() + ", came across illegal record");
          return;
        }
        final String dir = mapElem[0];
        final String checkoutDir = mapElem.length == 2 ? dir : mapElem[2];
        myProperties.put(dir, new DirInfo(mapElem[1], checkoutDir, mapElem.length == 4 ? mapElem[3] : null));
        fileRecord = reader.readLine();
      }
    } catch (IOException e) {
      myLogger.warn("Error reading directories states from " + myPropertiesFile.getAbsolutePath() + getMessage(e));
      myLogger.exception(e);
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
          myLogger.warn("Error closing directories states file " + myPropertiesFile.getAbsolutePath() + getMessage(e));
          myLogger.exception(e);
        }
      }
      if (!preserveFile) {
        deletePropertiesFile();
      }
    }
  }

  private String getMessage(IOException e) {
    return e.getMessage() == null ? "" : ": " + e.getMessage();
  }

  private void writeProperties() {
    if (myProperties.isEmpty()) {
      if (myPropertiesFile.isFile()) {
        deletePropertiesFile();
      }
      return;
    }
    BufferedWriter writer = null;
    try {
      writer = new BufferedWriter(new FileWriter(myPropertiesFile));
      for (Map.Entry<String, DirInfo> entry : myProperties.entrySet()) {
          writer.write(String.format("%s%s%s%s%s%s%s%n"
            , entry.getKey(), KEY_VAL_SEPARATOR
            , entry.getValue().state, KEY_VAL_SEPARATOR
            , entry.getValue().checkoutDir, KEY_VAL_SEPARATOR
            , entry.getValue().buildTypeId)
          );
      }
    } catch (IOException e) {
      myLogger.warn("Error saving directories states to " + myPropertiesFile.getAbsolutePath() + getMessage(e));
      myLogger.exception(e);
    } finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException e) {
          myLogger.warn("Error closing directories states file " + myPropertiesFile.getAbsolutePath());
          myLogger.exception(e);
        }
      }
    }
  }

  private void deletePropertiesFile() {
    if (!FileUtil.delete(myPropertiesFile)) {
      myLogger.warn("Error deleting directories states file " + myPropertiesFile.getAbsolutePath());
    }
  }

  private void cleanupPropertiesAndSnapshots(final List<File> actualCheckoutDirs) {
    try {
      readPropertiesNoAwait(false);

      final Set<String> savedDirs = myProperties.keySet();
      final List<File> snapshots = getSnapshotFiles();

      if (savedDirs.isEmpty() && snapshots.isEmpty()) {
        return;
      }

      final ArrayList<String> propertiesToRemove = new ArrayList<String>(savedDirs);
      final ArrayList<File> snapshotsToRemove = new ArrayList<File>(snapshots);

      for (File checkoutDir : actualCheckoutDirs) {
        if (!checkoutDir.isDirectory()) {
          continue;
        }
        final String unifiedCheckoutDirPath = unifyPath(checkoutDir);
        for (Map.Entry<String, DirInfo> entry : myProperties.entrySet()) {
          final DirInfo info = entry.getValue();
          final String monitoredDir = entry.getKey();
          if (info.checkoutDir.equals(unifiedCheckoutDirPath)) {
            propertiesToRemove.remove(unifyPath(monitoredDir));
            snapshotsToRemove.remove(getSnapshotFile(new File(monitoredDir)));
          }
        }
      }

      for (String s : propertiesToRemove) {
        myProperties.remove(s);
      }

      writeProperties();

      for (File f : snapshotsToRemove) {
        FileUtil.delete(f);
      }
    } finally {
      myCleanupFinishedSignal.countDown();
    }
  }

  private List<File> getSnapshotFiles() {
    return Arrays.asList(myPropertiesFile.getParentFile().listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        return pathname.getName().endsWith(SNAPSHOT_SUFFIX);
      }
    }));
  }

  public File getSnapshotFile(File dir) {
    return new File(myPropertiesFile.getParent(), Integer.toHexString(dir.hashCode()) + SNAPSHOT_SUFFIX);
  }

  static enum DirectoryState {
    UNKNOWN("unknown"),
    CLEAN("clean"),
    STRICT_CLEAN("strict_clean"),
    DIRTY("dirty"),
    PENDING("pending"),
    STRICT_PENDING("strict_pending");

    private static final List<DirectoryState> ourStates = Arrays.asList(UNKNOWN, CLEAN, STRICT_CLEAN, DIRTY, PENDING, STRICT_PENDING);

    public static DirectoryState getState(@Nullable String str) {
      if (str == null) return UNKNOWN;
      for (final DirectoryState state : ourStates) {
        if (state.getName().equals(str)) return state;
      }
      return UNKNOWN;
    }

    @NotNull
    private final String myName;

    private DirectoryState(@NotNull String name) {
      myName = name;
    }

    @NotNull
    public String getName() {
      return myName;
    }
  }

  public DirectoryState getState(File dir) {
    final DirInfo info = myProperties.get(unifyPath(dir));
    return DirectoryState.getState(info == null ? null: info.state);
  }

  public synchronized String getPreviousBuildTypeId(File dir) {
    final DirInfo info = myProperties.get(unifyPath(dir));
    return info == null ? null: info.buildTypeId;
  }

  private synchronized void mark(@NotNull final File dir, @NotNull final File checkoutDir, @NotNull String state, @NotNull String buildTypeId) {
    readProperties(false);
    myLogger.debug("Marking " + dir.getAbsolutePath() + " as " + state);
    myProperties.put(unifyPath(dir), new DirInfo(state, unifyPath(checkoutDir), buildTypeId));
    writeProperties();
  }

  public void markDirty(@NotNull final File dir, @NotNull final File checkoutDir, @NotNull String buildTypeId) {
    mark(dir, checkoutDir, DirectoryState.DIRTY.getName(), buildTypeId);
  }

  public void markClean(@NotNull File dir, @NotNull final File checkoutDir, boolean strict, @NotNull String buildTypeId) {
    mark(dir, checkoutDir, strict ? DirectoryState.STRICT_CLEAN.getName() : DirectoryState.CLEAN.getName(), buildTypeId);
  }

  public void markPending(@NotNull File dir, @NotNull final File checkoutDir, boolean strict, @NotNull String buildTypeId) {
    mark(dir, checkoutDir, strict ? DirectoryState.STRICT_PENDING.getName() : DirectoryState.PENDING.getName(), buildTypeId);
  }

  // for tests
  public boolean isInitialized(){
    return myCleanupFinishedSignal.getCount()==0;
  }

  private static final class DirInfo {
    @NotNull private final String state;
    @NotNull private final String checkoutDir;
    @Nullable private final String buildTypeId;


    private DirInfo(@NotNull final String state, @NotNull final String checkoutDir, @Nullable final String buildTypeId) {
      this.state = state;
      this.checkoutDir = checkoutDir;
      this.buildTypeId = buildTypeId;
    }
  }
}
