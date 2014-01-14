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
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.tools.ServerToolProcessor;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 26.04.11
 * Time: 22:10
 */
public class HandleToolServerProcessor implements ServerToolProcessor {
  private static final Logger LOG = Logger.getLogger(HandleToolServerProcessor.class.getName());

  @NotNull
  private final HandleProvider myHandleProvider;

  public HandleToolServerProcessor(@NotNull final HandleProvider handleProvider) {
    myHandleProvider = handleProvider;
  }

  @NotNull
  public String getType() {
    return "handleTool";
  }

  public void processTool(@NotNull final File tool, @NotNull final ServerToolProcessorCallback callback) {
    try {
      myHandleProvider.packHandleTool(tool);
      callback.progress("Saved " + myHandleProvider.getHandleExe(), Status.NORMAL);
      callback.progress("handle.exe will be present on agents after the upgrade process (will start automatically)", Status.NORMAL);
    } catch (Throwable throwable) {
      final String err = "Failed to save " + myHandleProvider.getHandleExe();
      LOG.error(err, throwable);
      callback.progress("Failed to save " + myHandleProvider.getHandleExe() + ", please see teamcity-server.log for details", Status.ERROR);
    }
  }
}
