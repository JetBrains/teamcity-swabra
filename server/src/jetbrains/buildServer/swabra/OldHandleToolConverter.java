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
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.tools.ServerToolPreProcessorAdapter;
import jetbrains.buildServer.tools.ToolException;
import jetbrains.buildServer.tools.installed.ToolPaths;
import jetbrains.buildServer.util.ArchiveUtil;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Evgeniy.Koshkin on 21-Mar-16.
 */
public class OldHandleToolConverter extends ServerToolPreProcessorAdapter {

  private static final Logger LOG = Logger.getLogger(HandleProvider.class.getName());

  private final ServerPaths myServerPaths;
  @NotNull private final ToolPaths myToolPaths;

  public OldHandleToolConverter(@NotNull final ServerPaths serverPaths, @NotNull final ToolPaths toolPaths) {
    myServerPaths = serverPaths;
    myToolPaths = toolPaths;
  }

  @NotNull
  @Override
  public String getName() {
    return HandleToolType.HANDLE_TOOL_TYPE_NAME;
  }

  @Override
  public void doBeforeServerStartup() throws ToolException {
    final File oldPlugin1 = new File(myServerPaths.getPluginsDir(), "handle-provider");
    if (oldPlugin1.exists()) {
      LOG.debug("Detected old handle-provider plugin " + oldPlugin1);
      try {
        final File validExeToolLocation = myToolPaths.getSharedToolPath(new File("handle.exe"));
        if (!validExeToolLocation.isFile()) {
          LOG.debug("Converting old handle-provider plugin " + oldPlugin1 + " into tool");
          final File agentPlugin = new File(oldPlugin1, "agent/handle-provider.zip");
          if (!agentPlugin.isFile()) {
            LOG.warn("No agent plugin detected in " + oldPlugin1);
          } else {
            final File temp = new File(FileUtil.getTempDirectory(), "handle-provider");
            try {
              ArchiveUtil.unpackZip(agentPlugin, "", temp);
              final File oldHandleExe = new File(temp, "handle-provider/bin/handle.exe");
              if (oldHandleExe.isFile()) {
                FileUtil.copy(oldHandleExe, validExeToolLocation);
              } else {
                LOG.warn("No handle.exe detected in " + oldPlugin1);
              }
            } catch (IOException e) {
              throw new ToolException("Failed to extract handle.exe from " + agentPlugin, e);
            } finally {
              FileUtil.delete(temp);
            }
          }
        }
      } finally {
        LOG.debug("Deleting old handle-provider plugin " + oldPlugin1);
        FileUtil.delete(oldPlugin1);
      }
    }
    FileUtil.delete(new File(myServerPaths.getPluginDataDirectory(), "handle-provider.zip"));
  }
}
