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
import jetbrains.buildServer.tools.ServerToolProviderAdapter;
import jetbrains.buildServer.tools.ToolException;
import jetbrains.buildServer.tools.ToolType;
import jetbrains.buildServer.tools.ToolVersion;
import jetbrains.buildServer.tools.utils.URLDownloader;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.swabra.HandleToolType.HANDLE_EXE;
import static jetbrains.buildServer.swabra.HandleToolType.HANDLE_TOOL;

/**
 * User: vbedrosova
 * Date: 26.02.2010
 * Time: 15:48:35
 */
public class HandleProvider extends ServerToolProviderAdapter {
  private static final Logger LOG = org.apache.log4j.Logger.getLogger(HandleProvider.class.getName());

  @NotNull private final HandleToolType myHandleToolType = new HandleToolType();
  @NotNull private final ToolVersion mySingleToolVersion = new ToolVersion() {
    @NotNull
    @Override
    public ToolType getType() {
      return myHandleToolType;
    }

    @NotNull
    @Override
    public String getVersion() {
      return "latest";
    }

    @NotNull
    @Override
    public String getId() {
      return HANDLE_TOOL;
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return myHandleToolType.getDisplayName() + " latest version";
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ToolVersion that = (ToolVersion)o;

      if (!myHandleToolType.getType().equals(that.getType().getType())) return false;
      return HANDLE_TOOL.equals(that.getVersion());

    }

    @Override
    public int hashCode() {
      int result = myHandleToolType.getType().hashCode();
      result = 31 * result + HANDLE_TOOL.hashCode();
      return result;
    }
  };

  @NotNull
  @Override
  public ToolType getType() {
    return myHandleToolType;
  }

  @NotNull
  @Override
  public Collection<ToolVersion> getAvailableToolVersions() {
    return Collections.singleton(mySingleToolVersion);
  }

  @NotNull
  @Override
  public File fetchToolPackage(@NotNull final ToolVersion toolVersion, @NotNull final File targetDirectory) throws ToolException {
    final File location = new File(targetDirectory, HANDLE_EXE);
    try {
      URLDownloader.download(new URL(HandleToolType.HTTP_LIVE_SYSINTERNALS_COM_HANDLE_EXE), location);
    } catch (MalformedURLException e) {
      throw new ToolException("Failed to fetch " + HANDLE_TOOL, e);
    }
    LOG.debug("Successfully downloaded Sysinternals handle.exe to " + location);
    return location;
  }

  @Override
  public void unpackToolPackage(@NotNull final File toolPackage, @NotNull final File targetDirectory) throws ToolException {
    try {
      if(toolPackage.isDirectory())
        FileUtil.copyDir(toolPackage, targetDirectory);
      else
        FileUtil.copy(toolPackage, new File(targetDirectory, HANDLE_EXE));
    } catch (IOException e) {
      throw new ToolException("Failed to copy " + HANDLE_TOOL + " to " + targetDirectory, e);
    }
  }

  @Nullable
  @Override
  public ToolVersion tryGetPackageVersion(@NotNull final File toolPackage) {
    final String toolPackageName = toolPackage.getName();
    return ((toolPackage.isDirectory() && toolPackageName.equalsIgnoreCase(HANDLE_TOOL)) || (toolPackage.isFile() && toolPackageName.equalsIgnoreCase(HANDLE_EXE)))
           ? mySingleToolVersion : null;
  }
}