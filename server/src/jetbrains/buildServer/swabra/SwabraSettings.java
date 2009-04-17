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
  public SwabraSettings(@NotNull final PagePlaces pagePlaces, @NotNull final ProjectManager projectManager) {
    List<String> supportedRunTypes = Arrays.asList("Ant", "simpleRunner", "rcodedup", "Duplicator", "FxCop",
      "Inspection", "Ipr", "Maven2", "MSBuild", "NAnt", "rake-runner", "sln2003", "sln2005", "sln2008");

    final EditBuildRunnerSettingsExtension editSettingsExtension =
      new EditBuildRunnerSettingsExtension(pagePlaces, supportedRunTypes);
    editSettingsExtension.setPluginName("swabra");
    editSettingsExtension.setIncludeUrl("swabraSettings.jsp");
    editSettingsExtension.register();

    final ViewBuildRunnerSettingsExtension viewSettingsExtension =
      new ViewBuildRunnerSettingsExtension(projectManager, pagePlaces, supportedRunTypes);
    viewSettingsExtension.setPluginName("swabra");
    viewSettingsExtension.setIncludeUrl("viewSwabraSettings.jsp");
    viewSettingsExtension.register();
  }
}
