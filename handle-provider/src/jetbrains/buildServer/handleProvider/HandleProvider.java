/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package jetbrains.buildServer.handleProvider;

import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * User: vbedrosova
 * Date: 25.02.2010
 * Time: 16:21:05
 */
public class HandleProvider {
  public static final String HANDLE_EXE_PATH = "handle.exe.path";

  private static boolean notDefined(String value) {
    return (value == null) || ("".equals(value));
  }

  public HandleProvider(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher,
                        @NotNull final BuildAgentConfiguration config) {
    if (!SystemInfo.isWindows) {
      return;
    }
    if (notDefined(System.getProperty(HANDLE_EXE_PATH))) {
      final File pluginHandle = FileUtil.getCanonicalFile(new File(config.getAgentPluginsDirectory(),
        "handle-provider/bin/handle.exe"));
      System.setProperty(HANDLE_EXE_PATH, pluginHandle.getAbsolutePath());
    }
  }
}
