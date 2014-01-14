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

package jetbrains.buildServer.swabra.serverHealth;

import java.util.List;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;

/**
 * User: Victory.Bedrosova
 * Date: 4/19/13
 * Time: 6:00 PM
 */
public final class SwabraSettingsGroup {
  @NotNull private final SwabraSettings mySettings;
  @NotNull private final List<SBuildType> myBuildTypes;

  public SwabraSettingsGroup(@NotNull final SwabraSettings settings,
                             @NotNull final List<SBuildType> buildTypes) {
    mySettings = settings;
    myBuildTypes = buildTypes;
  }


  @Used("jsp")
  @NotNull
  public SwabraSettings getSettings() {
    return mySettings;
  }

  @Used("jsp")
  @NotNull
  public List<SBuildType> getBuildTypes() {
    return myBuildTypes;
  }
}
