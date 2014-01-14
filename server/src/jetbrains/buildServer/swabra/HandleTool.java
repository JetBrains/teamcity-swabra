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

package jetbrains.buildServer.swabra;

import jetbrains.buildServer.tools.ToolTypeExtension;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 25.04.11
 * Time: 16:38
 */
public class HandleTool extends ToolTypeExtension {
  @NotNull
  public String getType() {
    return "handleTool";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return "Sysinternals handle.exe";
  }

  @Override
  public String getDescription() {
    return "On Windows agents handle.exe is used to determine processes which hold files in the checkout directory. " +
           "handle.exe will be automatically distributed to the agents and the agents will restart";
  }

  @Override
  public String getFileName() {
    return "handle.exe";
  }

  @Override
  public boolean isSupportDownload() {
    return true;
  }

  @Override
  public String getDownloadUrl() {
    return "http://live.sysinternals.com/handle.exe";
  }

  @Override
  public String getToolSiteUrl() {
    return "http://technet.microsoft.com/en-us/sysinternals/bb896655";
  }

  @Override
  public String getToolLicenseUrl() {
    return "http://technet.microsoft.com/en-us/sysinternals/bb469936.aspx";
  }

  @Override
  public String getTeamCityHelpFile() {
    return "Build+Files+Cleaner+%28Swabra%29";
  }

  @Override
  public String getTeamCityHelpAnchor() {
    return "DownloadingHandle";
  }
}
