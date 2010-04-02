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

import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * User: vbedrosova
 * Date: 18.11.2009
 * Time: 16:50:49
 */

public final class SwabraPropertiesProcessor {
  private static final String FILE_NAME = "snapshot.map";
  private static final String KEY_VAL_SEPARATOR = "=";

  private static final String DIRTY = "[dirty]";
  private static final String CLEAN = "[clean]";

  @NotNull
  private Map<String, String> myProperties;
  @NotNull
  private final SwabraLogger myLogger;
  @NotNull
  private final File myPropertiesFile;

  private final CountDownLatch myCleanupFinishedSignal;

  public static String unifyPath(@NotNull File dir) {
    return dir.getAbsolutePath().replace("\\", "/");
  }

  public SwabraPropertiesProcessor(@NotNull File tempDir, @NotNull SwabraLogger logger) {
    myPropertiesFile = new File(tempDir, FILE_NAME);
    myLogger = logger;
    myCleanupFinishedSignal = new CountDownLatch(1);
  }

  public void markDirty(@NotNull File dir) {
    final String path = dir.getAbsolutePath();
    final String state = myProperties.get(path);
    if (state == null || DIRTY.equals(state)) {
      return;
    }
    myLogger.swabraDebug("Marking " + path + " as dirty");
    myProperties.put(unifyPath(dir), DIRTY);
    writeProperties();
  }

  public void markClean(@NotNull File dir) {
    myLogger.swabraDebug("Marking " + dir.getAbsolutePath() + " as clean");
    myProperties.put(unifyPath(dir), CLEAN);
    writeProperties();
  }

  public void setSnapshot(@NotNull File dir, @NotNull String snapshot) {
    myLogger.swabraDebug("Setting snapshot " + snapshot + " for " + dir.getAbsolutePath());
    myProperties.put(unifyPath(dir), snapshot);
    writeProperties();
  }

  public boolean isDirty(@NotNull File dir) {
    final String info = myProperties.get(unifyPath(dir));
    return (info == null) || DIRTY.equals(info);
  }

  public boolean isClean(@NotNull File dir) {
    return CLEAN.equals(myProperties.get(unifyPath(dir)));
  }

  public String getSnapshot(@NotNull File dir) {
    return myProperties.get(unifyPath(dir));
  }

  public void deleteRecord(@NotNull File dir) {
    myProperties.remove(unifyPath(dir));
    writeProperties();
  }

  public void readProperties() {
    try {
      myCleanupFinishedSignal.await();
    } catch (InterruptedException e) {
      myLogger.swabraWarn("Thread interrupted");
    }
    readPropertiesNoAwait();
  }

  private void readPropertiesNoAwait() {
    myProperties = new HashMap<String, String>();
    if (!myPropertiesFile.isFile()) {
      myLogger.swabraMessage("Couldn't read checkout directories states from " + myPropertiesFile.getAbsolutePath() + ", no file present", false);
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
      if (!FileUtil.delete(myPropertiesFile)) {
        myLogger.swabraWarn("Error deleting checkout directories states file " + myPropertiesFile.getAbsolutePath());
      }
    }
  }

  public void writeProperties() {
    if (myProperties.isEmpty()) {
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

  public void cleanupProperties(final File[] actualCheckoutDirs) {
    if (actualCheckoutDirs == null || actualCheckoutDirs.length == 0) {
      return;
    }
    new Thread(new Runnable() {
      public void run() {
        try {
          readPropertiesNoAwait();
          final Set<String> savedCheckoutDirs = myProperties.keySet();
          if (savedCheckoutDirs.isEmpty()) {
            return;
          }
          final ArrayList<String> toRemove = new ArrayList<String>(savedCheckoutDirs);
          for (File dir : actualCheckoutDirs) {
            if (!dir.isDirectory()) {
              continue;
            }
            toRemove.remove(unifyPath(dir));
          }
          for (String s : toRemove) {
            myProperties.remove(s);
          }
          writeProperties();
        } finally {
          myCleanupFinishedSignal.countDown();
        }
      }
    }).start();
  }
}
