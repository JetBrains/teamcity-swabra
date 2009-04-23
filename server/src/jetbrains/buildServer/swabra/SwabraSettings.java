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

import java.util.List;
import java.util.Arrays;

import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.buildType.ViewBuildRunnerSettingsExtension;
import jetbrains.buildServer.web.openapi.buildType.EditBuildRunnerSettingsExtension;
import jetbrains.buildServer.serverSide.ProjectManager;

/**
 * User: vbedrosova
 * Date: 17.04.2009
 * Time: 15:20:43
 */
public class SwabraSettings {
  private List<String> mySupportedRunTypes = Arrays.asList("Ant", "simpleRunner");
  private final PagePlaces myPagePlaces;
  private final ProjectManager myProjectManager;

  public SwabraSettings(@NotNull final PagePlaces pagePlaces, @NotNull final ProjectManager projectManager) {
    myPagePlaces = pagePlaces;
    myProjectManager = projectManager;
  }

  public void setSupportedRunTypes(final List<String> supportedRunTypes) {
    mySupportedRunTypes = supportedRunTypes;
  }

  public void registerExtensions() {
    final EditBuildRunnerSettingsExtension editSettingsExtension = new EditBuildRunnerSettingsExtension(myPagePlaces, mySupportedRunTypes);
    editSettingsExtension.setPluginName("swabra");
    editSettingsExtension.setIncludeUrl("swabraSettings.jsp");
    editSettingsExtension.register();
    final ViewBuildRunnerSettingsExtension viewSettingsExtension = new ViewBuildRunnerSettingsExtension(myProjectManager, myPagePlaces, mySupportedRunTypes);
    viewSettingsExtension.setPluginName("swabra");
    viewSettingsExtension.setIncludeUrl("viewSwabraSettings.jsp");
    viewSettingsExtension.register();
  }
}
