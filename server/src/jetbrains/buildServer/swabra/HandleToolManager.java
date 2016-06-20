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
import jetbrains.buildServer.tools.ToolException;
import jetbrains.buildServer.tools.installed.ToolsRegistry;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.swabra.HandleTool.HANDLE_EXE;
import static jetbrains.buildServer.swabra.HandleTool.HANDLE_TOOL;

/**
 * Created by Evgeniy.Koshkin.
 */
public class HandleToolManager {

  private static final Logger LOG = org.apache.log4j.Logger.getLogger(HandleToolManager.class.getName());

  private final ToolsRegistry myToolsRegistry;

  public HandleToolManager(final ToolsRegistry toolsRegistry) {
    myToolsRegistry = toolsRegistry;
  }

  boolean isHandlePresent() {
    return myToolsRegistry.isToolRegistered(HANDLE_TOOL);
  }

  @NotNull
  File getHandleExe() {
    return new File(myToolsRegistry.getRegisteredToolPath(HANDLE_TOOL), HANDLE_EXE);
  }

  void packHandleTool(@NotNull File handleTool) throws ToolException {
    if (myToolsRegistry.isToolRegistered(HANDLE_TOOL)) {
      LOG.debug("Updating " + handleTool + " tool. Removing old one.");
      myToolsRegistry.removeTool(HANDLE_TOOL);
    }
    LOG.debug("Packaging " + handleTool + " as tool");
    myToolsRegistry.installTool(handleTool);
  }
}
