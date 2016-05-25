/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import jetbrains.buildServer.tools.*;
import jetbrains.buildServer.tools.web.actions.URLDownloader;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.swabra.HandleTool.HANDLE_EXE;
import static jetbrains.buildServer.swabra.HandleTool.HANDLE_TOOL;

/**
 * User: vbedrosova
 * Date: 26.02.2010
 * Time: 15:48:35
 */
public class HandleProvider extends ServerToolProviderAdapter {
  private static final Logger LOG = org.apache.log4j.Logger.getLogger(HandleProvider.class.getName());

  @NotNull private final HandleTool myHandleTool;
  @NotNull private final ToolVersion mySingleToolVersion;

  public HandleProvider(@NotNull final HandleTool handleTool) {
    myHandleTool = handleTool;
    mySingleToolVersion = new SimpleToolVersion(myHandleTool, "Latest");
  }

  @NotNull
  @Override
  public ToolType getType() {
    return myHandleTool;
  }

  @NotNull
  @Override
  public Collection<ToolVersion> getAvailableToolVersions() {
    return Collections.singleton(mySingleToolVersion); //TODO: provide version
  }

  @NotNull
  @Override
  public File fetchToolPackage(@NotNull final ToolVersion toolVersion, @NotNull final File targetDirectory) throws ToolException {
    final File location = new File(targetDirectory, HANDLE_EXE);
    try {
      URLDownloader.download(new URL(HandleTool.HTTP_LIVE_SYSINTERNALS_COM_HANDLE_EXE), location);
    } catch (MalformedURLException e) {
      throw new ToolException("Failed to fetch " + HANDLE_TOOL, e);
    }
    LOG.debug("Successfully downloaded Sysinternals handle.exe to " + location);
    return location;
  }

  @Override
  public void unpackToolPackage(@NotNull final File toolPackage, @NotNull final File targetDirectory) throws ToolException {
    try {
      FileUtil.copy(toolPackage, new File(targetDirectory, HANDLE_EXE));
    } catch (IOException e) {
      throw new ToolException("Failed to copy " + HANDLE_TOOL + " to " + targetDirectory, e);
    }
  }

  @Nullable
  @Override
  public ToolVersion tryGetPackageVersion(@NotNull final File toolPackage) {
    return toolPackage.getName().equalsIgnoreCase(HANDLE_EXE) ? mySingleToolVersion : null;
  }
}