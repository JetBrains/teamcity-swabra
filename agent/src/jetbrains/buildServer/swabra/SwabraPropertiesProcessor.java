/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Map;
import java.util.HashMap;

import jetbrains.buildServer.util.FileUtil;

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

  public static String unifyPath(@NotNull File dir) {
    return dir.getAbsolutePath().replace("/", File.separator).replace("\\", File.separator);   
  }

  public SwabraPropertiesProcessor(@NotNull File tempDir, @NotNull SwabraLogger logger) {
    myPropertiesFile = new File(tempDir, FILE_NAME);
    myLogger = logger;
  }

  public void markDirty(@NotNull File dir) {
    myLogger.debug("Swabra: Marking " + dir.getAbsolutePath() + " as dirty");
    myProperties.put(unifyPath(dir), DIRTY);
  }

  public void markClean(@NotNull File dir) {
    myLogger.debug("Swabra: Marking " + dir.getAbsolutePath() + " as clean");
    myProperties.put(unifyPath(dir), CLEAN);
  }

  public void setSnapshot(@NotNull File dir, @NotNull String snapshot) {
    myLogger.debug("Swabra: Setting snapshot " + snapshot + " for " + dir.getAbsolutePath());
    myProperties.put(unifyPath(dir), snapshot);
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
  }

  public void readProperties() {
    myProperties = new HashMap<String, String>();
    if (!myPropertiesFile.isFile()) {
      myLogger.message("Swabra: Couldn't read checkout directories states from " + myPropertiesFile.getAbsolutePath() + ", no file present", false);
      return;
    }
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(myPropertiesFile));
      String fileRecord = reader.readLine();
      while (fileRecord != null) {
        final String[] mapElem = fileRecord.split(KEY_VAL_SEPARATOR);
        if (mapElem.length != 2) {
          myLogger.error("Swabra: Error reading checkout directories states from " + myPropertiesFile.getAbsolutePath() + ", came across illegal record");
          return;
        }
        myProperties.put(mapElem[0], mapElem[1]);
        fileRecord = reader.readLine();
      }
    } catch (IOException e) {
      myLogger.error("Swabra: Error reading checkout directories states from " + myPropertiesFile.getAbsolutePath());
      myLogger.exception(e, true);
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
          myLogger.error("Swabra: Error closing checkout directories states file " + myPropertiesFile.getAbsolutePath());
          myLogger.exception(e, true);
        }
      }
      if (!FileUtil.delete(myPropertiesFile)) {
        myLogger.error("Swabra: Error deleting checkout directories states file " + myPropertiesFile.getAbsolutePath());
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
      myLogger.error("Swabra: Error saving checkout directories states to " + myPropertiesFile.getAbsolutePath());
      myLogger.exception(e, true);
    } finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException e) {
          myLogger.error("Swabra: Error closing checkout directories states file " + myPropertiesFile.getAbsolutePath());
          myLogger.exception(e, true);
        }
      }
    }
  }
}
