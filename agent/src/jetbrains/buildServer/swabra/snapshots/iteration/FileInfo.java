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

package jetbrains.buildServer.swabra.snapshots.iteration;

import jetbrains.buildServer.swabra.SwabraUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 30.01.2010
 * Time: 16:13:58
 */
public class FileInfo {
  @NotNull
  private final String myPath;
  private final long myLength;
  private final long myLastModified;
  private final boolean myIsFile;

  public FileInfo(@NotNull String path, long length, long lastModified, boolean isFile) {
    myPath = SwabraUtil.unifyPath(path);
    myLength = length;
    myLastModified = lastModified;
    myIsFile = isFile;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  public long getLastModified() {
    return myLastModified;
  }

  public long getLength() {
    return myLength;
  }

  public boolean isFile() {
    return myIsFile;
  }

  @Override
  public String toString() {
    return "FileInfo{" +
           "myPath='" + myPath + '\'' +
           ", myIsFile=" + myIsFile +
           ", myLength=" + myLength +
           ", myLastModified=" + myLastModified +
           '}';
  }
}