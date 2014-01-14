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


import java.io.File;
import java.io.IOException;
import jetbrains.buildServer.serverSide.AgentToolManager;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.ArchiveUtil;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 26.02.2010
 * Time: 15:48:35
 */
public class HandleProvider {
  private static final org.apache.log4j.Logger LOG = org.apache.log4j.Logger.getLogger(HandleProvider.class.getName());

  private static final String HANDLE_TOOL = "SysinternalsHandle";

  @NotNull
  private final ServerPaths myServerPaths;
  
  @NotNull
  private final AgentToolManager myToolManager;

  public HandleProvider(@NotNull final ServerPaths paths,
                        @NotNull final AgentToolManager toolManager) {
    myServerPaths = paths;
    myToolManager = toolManager;

    convertOldHandlePlugin();
  }

  public boolean isHandlePresent() {
    return myToolManager.isToolRegistered(HANDLE_TOOL);
  }

  @NotNull
  public File getHandleExe() {
    return new File(myToolManager.getRegisteredToolPath(HANDLE_TOOL), "handle.exe");
  }

  public void packHandleTool(@NotNull File handleTool) throws IOException {
    if (myToolManager.isToolRegistered(HANDLE_TOOL)) {
      LOG.debug("Updating " + handleTool + " tool");
      FileUtil.delete(getHandleExe());
      FileUtil.copyDir(handleTool, myToolManager.getRegisteredToolPath(HANDLE_TOOL));
    } else {
      LOG.debug("Packaging " + handleTool + " as tool");
      myToolManager.registerSharedTool(HANDLE_TOOL, handleTool);
    }
  }

  private void convertOldHandlePlugin() {
    final File oldPlugin1 = new File(myServerPaths.getPluginsDir(), "handle-provider");
    if (oldPlugin1.exists()) {
      LOG.debug("Detected old handle-provider plugin " + oldPlugin1);
      try {
        if (!getHandleExe().isFile()) {
          LOG.debug("Converting old handle-provider plugin " + oldPlugin1 + " into tool");
          final File agentPlugin = new File(oldPlugin1, "agent/handle-provider.zip");
          if (agentPlugin.isFile()) {
            final File temp = new File(FileUtil.getTempDirectory(), "handle-provider");
            try {
              ArchiveUtil.unpackZip(agentPlugin, "", temp);

              final File handleExe = new File(temp, "handle-provider/bin/handle.exe");
              if (handleExe.isFile()) {
                packHandleTool(handleExe.getParentFile());
              } else {
                LOG.warn("No handle.exe detected in " + oldPlugin1);
              }
            } finally {
              FileUtil.delete(temp);
            }
          } else {
            LOG.warn("No agent plugin detected in " + oldPlugin1);
          }
        }
      } catch (IOException e) {
        LOG.warn("Failed to convert " + oldPlugin1, e);
      } finally {
        LOG.debug("Deleting old handle-provider plugin " + oldPlugin1);
        FileUtil.delete(oldPlugin1);
      }
    }
    FileUtil.delete(new File(myServerPaths.getPluginDataDirectory(), "handle-provider.zip"));
  }
}