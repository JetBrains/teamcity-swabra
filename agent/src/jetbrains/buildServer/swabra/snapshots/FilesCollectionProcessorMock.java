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
import java.io.IOException;
import java.io.RandomAccessFile;
import jetbrains.buildServer.swabra.SwabraLogger;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 04.02.2010
 * Time: 14:59:20
 */
public class FilesCollectionProcessorMock extends FilesCollectionProcessor {
  private final String myLogPath;
  private RandomAccessFile myFile;

  public FilesCollectionProcessorMock(@NotNull SwabraLogger logger,
                                      LockedFileResolver resolver,
                                      @NotNull File dir,
                                      boolean verbose,
                                      boolean strict,
                                      String logPath) {
    super(logger, resolver, dir, verbose, strict);
    myLogPath = logPath;
  }

  private void log(String message) {
    try {
      for (int i = 0; i < message.length(); ++i) {
        myFile.writeByte(message.charAt(i));
      }
//      myFile.writeChars(message);
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
      myFile.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void comparisonStarted() {
    super.comparisonStarted();
    try {
      myFile = new RandomAccessFile(myLogPath, "rw");
      myFile.seek(myFile.length());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
