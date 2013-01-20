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
import jetbrains.buildServer.swabra.SwabraLogger;
import jetbrains.buildServer.swabra.SwabraSettings;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 13.05.2010
 * Time: 15:52:19
 */
public class FilesCollectionRulesAwareProcessor extends FilesCollectionProcessor {
  private final SwabraRules myRules;

  public FilesCollectionRulesAwareProcessor(@NotNull SwabraLogger logger,
                                            LockedFileResolver resolver,
                                            @NotNull File dir,
                                            SwabraSettings settings) {
    super(logger, resolver, dir, settings.isVerbose(), settings.isLockingProcessesKill());

    myRules = settings.getRules();
  }

  @Override
  public boolean willProcess(FileInfo info) {
    if (super.willProcess(info)) {
      return myRules.shouldInclude(info.getPath());
    }
    return false;
  }
}


