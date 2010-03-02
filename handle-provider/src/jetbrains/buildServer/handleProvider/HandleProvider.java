package jetbrains.buildServer.handleProvider;

import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * User: vbedrosova
 * Date: 25.02.2010
 * Time: 16:21:05
 */
public class HandleProvider extends AgentLifeCycleAdapter {
  private static final Logger LOG = Logger.getLogger(HandleProvider.class);

  @NotNull
  private final BuildAgentConfiguration myConfig;

  public HandleProvider(@NotNull final EventDispatcher<AgentLifeCycleListener> agentDispatcher,
                        @NotNull final BuildAgentConfiguration config) {
    agentDispatcher.addListener(this);
    myConfig = config;
  }

  @Override
  public void agentStarted(@NotNull BuildAgent agent) {
    if (!SystemInfo.isWindows) {
      return;
    }
    final File pluginHandle = FileUtil.getCanonicalFile(new File(myConfig.getAgentPluginsDirectory(), "handle-provider/bin/handle.exe"));
    final File agentHandle = new File(myConfig.getCacheDirectory("handle"), "handle.exe");
    try {
      FileUtil.copy(pluginHandle, agentHandle);
    } catch (IOException e) {
      LOG.error("handle-provider: Couldn't copy handle.exe from " + pluginHandle.getAbsolutePath()
        + " to " + agentHandle.getAbsolutePath() + " " + e);
    }
  }
}
