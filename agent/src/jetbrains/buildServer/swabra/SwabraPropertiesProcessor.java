/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static jetbrains.buildServer.swabra.SwabraUtil.unifyPath;

/**
 * User: vbedrosova
 * Date: 18.11.2009
 * Time: 16:50:49
 */

final class SwabraPropertiesProcessor extends AgentLifeCycleAdapter {
  private static final String FILE_NAME = "snapshot.map";
  private static final String KEY_VAL_SEPARATOR = "=";

  private static final String SNAPSHOT_SUFFIX = ".snapshot";
  private static final String MARK = "*";

  private Map<String, String> myProperties;
  private final SwabraLogger myLogger;
  private File myPropertiesFile;

  private CountDownLatch myCleanupFinishedSignal;

  public SwabraPropertiesProcessor(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher,
                                   @NotNull final SwabraLogger logger) {
    agentDispatcher.addListener(this);
    myLogger = logger;
  }

  @Override
  public void afterAgentConfigurationLoaded(@NotNull BuildAgent agent) {
    myPropertiesFile = new File(agent.getConfiguration().getCacheDirectory(Swabra.CACHE_KEY), FILE_NAME);
  }

  @Override
  public void agentStarted(@NotNull BuildAgent agent) {
    myCleanupFinishedSignal = new CountDownLatch(1);

    final File[] actualCheckoutDirs = agent.getConfiguration().getWorkDirectory().listFiles();
    if (actualCheckoutDirs == null) {
      return;
    }
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

  @NotNull
  public File getPropertiesFile() {
    return myPropertiesFile;
  }

  public synchronized void deleteRecord(@NotNull File dir) {
    readProperties(false);
    myProperties.remove(unifyPath(dir));
    writeProperties();
  }

  private void readProperties(boolean preserveFile) {
    if (myCleanupFinishedSignal != null) {
      try {
        myCleanupFinishedSignal.await();
      } catch (InterruptedException e) {
        myLogger.swabraWarn("Thread interrupted");
      }
    }
    readPropertiesNoAwait(preserveFile);
  }

  private void readPropertiesNoAwait(boolean preserveFile) {
    myProperties = new HashMap<String, String>();
    if (!myPropertiesFile.isFile()) {
      myLogger.swabraDebug("Couldn't read checkout directories states from " + myPropertiesFile.getAbsolutePath() + ", no file present");
      return;
    }
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(myPropertiesFile));
      String fileRecord = reader.readLine();
      while (fileRecord != null) {
        final String[] mapElem = fileRecord.split(KEY_VAL_SEPARATOR);
        if (mapElem.length != 2) {
          myLogger.swabraWarn("Error reading checkout directories states from " + myPropertiesFile.getAbsolutePath() + ", came across illegal record");
          return;
        }
        myProperties.put(mapElem[0], mapElem[1]);
        fileRecord = reader.readLine();
      }
    } catch (IOException e) {
      myLogger.swabraWarn("Error reading checkout directories states from " + myPropertiesFile.getAbsolutePath());
      myLogger.exception(e);
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
          myLogger.swabraWarn("Error closing checkout directories states file " + myPropertiesFile.getAbsolutePath());
          myLogger.exception(e);
        }
      }
      if (!preserveFile) {
        deletePropertiesFile();
      }
    }
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
      for (final Map.Entry<String, String> e : myProperties.entrySet()) {
        writer.write(e.getKey() + KEY_VAL_SEPARATOR + e.getValue() + "\n");
      }
    } catch (IOException e) {
      myLogger.swabraWarn("Error saving checkout directories states to " + myPropertiesFile.getAbsolutePath());
      myLogger.exception(e);
    } finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException e) {
          myLogger.swabraWarn("Error closing checkout directories states file " + myPropertiesFile.getAbsolutePath());
          myLogger.exception(e);
        }
      }
    }
  }

  private void deletePropertiesFile() {
    if (!FileUtil.delete(myPropertiesFile)) {
      myLogger.swabraWarn("Error deleting checkout directories states file " + myPropertiesFile.getAbsolutePath());
    }
  }

  private void cleanupPropertiesAndSnapshots(final File[] actualCheckoutDirs) {
    try {
      readPropertiesNoAwait(false);

      final Set<String> savedCheckoutDirs = myProperties.keySet();
      final List<File> snapshots = getSnapshotFiles();

      if (savedCheckoutDirs.isEmpty() && snapshots.isEmpty()) {
        return;
      }

      final ArrayList<String> propertiesToRemove = new ArrayList<String>(savedCheckoutDirs);
      final ArrayList<File> snapshotsToRemove = new ArrayList<File>(snapshots);

      for (File dir : actualCheckoutDirs) {
        if (!dir.isDirectory()) {
          continue;
        }
        propertiesToRemove.remove(unifyPath(dir));
        snapshotsToRemove.remove(getSnapshotFile(dir));
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

  public String getSnapshotName(File checkoutDirectory) {
    return Integer.toHexString(checkoutDirectory.hashCode()) + SNAPSHOT_SUFFIX;
  }

  public File getSnapshotFile(File checkoutDirectory) {
    return new File(myPropertiesFile.getParent(), Integer.toHexString(checkoutDirectory.hashCode()) + SNAPSHOT_SUFFIX);
  }

  private static final String DIRTY = "dirty";
  private static final String CLEAN = "clean";
  private static final String PENDING = "pending";
  private static final String STRICT_PENDING = "strict_pending";

  public static enum DirectoryState {
    UNKNOWN,
    CLEAN,
    DIRTY,
    PENDING,
    STRICT_PENDING
  }

  public DirectoryState getState(File checkoutDirectory) {
    final String info = myProperties.get(unifyPath(checkoutDirectory));

    if (info == null)
      return DirectoryState.UNKNOWN;
    if (CLEAN.equals(info))
      return DirectoryState.CLEAN;
    if (DIRTY.equals(info))
      return DirectoryState.DIRTY;
    if (PENDING.equals(info))
      return DirectoryState.PENDING;
    if (STRICT_PENDING.equals(info))
      return DirectoryState.STRICT_PENDING;

    return DirectoryState.UNKNOWN;
  }

  private synchronized void mark(@NotNull File dir, @NotNull String state) {
    readProperties(false);
    myLogger.swabraDebug("Marking " + dir.getAbsolutePath() + " as " + state);
    myProperties.put(unifyPath(dir), state);
    writeProperties();
  }

  public synchronized void markDirty(@NotNull File dir) {
    mark(dir, DIRTY);
  }

  public synchronized void markClean(@NotNull File dir) {
    mark(dir, CLEAN);
  }

  public synchronized void markPending(@NotNull File dir, boolean strict) {
    mark(dir, strict ? STRICT_PENDING : PENDING);
  }
}
