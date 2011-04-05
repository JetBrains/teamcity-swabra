/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agent.plugins.beans.PluginDescriptor;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 25.02.2010
 * Time: 16:21:05
 */
public class HandleProvider {
  private static final Logger LOG = Logger.getInstance(HandleProvider.class.getName());
  public static final String HANDLE_EXE_PATH = "handle.exe.path";


  public HandleProvider(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher,
                        @NotNull final BuildAgentConfiguration config,
                        @NotNull final PluginDescriptor descriptor) {

    agentDispatcher.addListener(new AgentLifeCycleAdapter() {
      @Override
      public void beforeAgentConfigurationLoaded(@NotNull final BuildAgent agent) {
        if (!agent.getConfiguration().getSystemInfo().isWindows()) return;

        final File pluginHandle = FileUtil.getCanonicalFile(new File(descriptor.getPluginRoot(), "bin/handle.exe"));

        if (!pluginHandle.isFile()) {
          LOG.warn("Failed to find handle.exe at path: " + pluginHandle + ". Swabra is corrupted.");
          return;
        }

        LOG.info("Registering handle.exe path: " + pluginHandle);
        config.addSystemProperty(HANDLE_EXE_PATH, pluginHandle.getPath());

        agentDispatcher.removeListener(this);
      }
    });
  }
}
