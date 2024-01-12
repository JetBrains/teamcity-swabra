

package jetbrains.buildServer.swabra;

import java.util.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.tools.InstalledToolVersionEx;
import jetbrains.buildServer.tools.ServerToolManager;
import jetbrains.buildServer.tools.ToolUsagesProvider;
import jetbrains.buildServer.tools.ToolVersion;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim Zaytsev (maxim.zaytsev@jetbrains.com)
 * @since 2019.1
 */
public class HandleToolUsageProvider implements ToolUsagesProvider {

  private final static String myKey = "teamcity.tools.provideHandleToolToAllAgents";

  private final ServerToolManager myServerToolManager;

  public HandleToolUsageProvider(final ServerToolManager serverToolManager) {
    myServerToolManager = serverToolManager;
  }

  @Override
  public List<ToolVersion> getRequiredTools(@NotNull final SRunningBuild build) {

    InstalledToolVersionEx handleTool = myServerToolManager.findInstalledTool(HandleToolVersion.getInstance().getId());
    if (handleTool == null) return Collections.emptyList();

    if (TeamCityProperties.getBoolean(myKey)) {
      return Collections.singletonList(handleTool);
    }

    if (!isHandleExeCompatibleWithAgent(build.getAgent())) return Collections.emptyList();

    Collection<SBuildFeatureDescriptor> features = build.getBuildFeaturesOfType(SwabraUtil.FEATURE_TYPE);
    Map<String, String> swabraParams = new HashMap<>();
    if (!features.isEmpty()) {
      swabraParams.putAll(features.iterator().next().getParameters());
    }
    swabraParams.putAll(build.getAgent().getConfigurationParameters());
    swabraParams.putAll(build.getBuildOwnParameters());
    boolean isToolRequired =  SwabraUtil.isLockingProcessesDetectionEnabled(swabraParams);
    return isToolRequired ? Collections.singletonList(handleTool) : Collections.emptyList();
  }

  private boolean isHandleExeCompatibleWithAgent(final SBuildAgent agent) {
    String osName = agent.getOperatingSystemName();
    if (isUnknownOS(osName)) {
      if (agent instanceof  BuildAgentEx) {
        osName = ((BuildAgentEx)agent).getAgentType().getOperatingSystemName();
      }
    }
    if (isUnknownOS(osName)) {
      return true;//in case of unknown os better is to say that handle.exe compatible
    }
    osName = osName.toLowerCase();
    return osName.startsWith("win") || osName.contains("windows");
  }

  private boolean isUnknownOS(final String osName) {
    return "N/A".equalsIgnoreCase(osName) || "<unknown>".equalsIgnoreCase(osName) || osName.isEmpty();
  }
}