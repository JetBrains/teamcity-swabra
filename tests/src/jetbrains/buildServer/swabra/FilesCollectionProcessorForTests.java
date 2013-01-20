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

package jetbrains.buildServer.swabra;

import java.io.File;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.FilesCollectionProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 11.06.2010
 * Time: 20:04:52
 */
public class FilesCollectionProcessorForTests extends FilesCollectionProcessor {
  public FilesCollectionProcessorForTests(@NotNull SwabraLogger logger,
                                          LockedFileResolver resolver,
                                          @NotNull File dir,
                                          boolean verbose, boolean strict) {
    super(logger, resolver, dir, verbose, strict);
  }

  @Override
  protected boolean resolveDelete(File f) {
    return true;
  }
}
