/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;

/**
 * User: vbedrosova
 * Date: 30.01.2010
 * Time: 14:21:51
 */
public interface FilesIterator {
  /**
   * Gets the next FileInfo
   * @return
   * @throws Exception
   */
  @Nullable FileInfo getNext() throws Exception;

  /**
   * skips iterator
   * @param dirInfo
   */
  void skipDirectory(FileInfo dirInfo);

  /**
   * stops iterators and closes all opened resources and handles
   * @throws Exception
   */
  void stopIterator();

  /**
   * Indicates whether iterator represent current state (not previously saved snapshot)
   * @return
   */
  boolean isCurrent();
}
