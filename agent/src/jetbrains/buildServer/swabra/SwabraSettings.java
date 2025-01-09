

package jetbrains.buildServer.swabra;

import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.util.*;
import jetbrains.buildServer.agent.AgentBuildFeature;
import jetbrains.buildServer.agent.AgentCheckoutMode;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BundledToolsRegistry;
import jetbrains.buildServer.agent.impl.operationModes.AgentOperationModeHolder;
import jetbrains.buildServer.swabra.processes.HandlePathProvider;
import jetbrains.buildServer.swabra.snapshots.SwabraRules;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: vbedrosova
 * Date: 04.05.2010
 * Time: 19:04:35
 */
public class SwabraSettings {
  private static final String DEFAULT_RULES_CONFIG_PARAM = "swabra.default.rules";
  private static final String IGNORED_PPRCESSES_CONFIG_PARAM = "swabra.ignored.processes";

  static final String[] DEFAULT_RULES = {"-:**/.svn", "-:**/.git", "-:**/.hg", "-:**/CVS", "-:.svn", "-:.git", "-:.hg", "-:CVS"};


  private boolean myCleanupEnabled;
  private final String myCleanupMode;
  private final boolean myStrict;

  private final boolean myLockingProcessesKill;
  private final boolean myLockingProcessesReport;

  private final boolean myVerbose;

  private File myHandlePath;

  private final SwabraRules myRules;

  private final File myCheckoutDir;
  @NotNull
  private final List<String> myIgnoredProcesses;


  public SwabraSettings(@NotNull AgentRunningBuild runningBuild, @NotNull AgentOperationModeHolder operationModeHolder) {
    final boolean isServiceMode = operationModeHolder.getOperationMode().isRegistrationOnServerSupported();
    if (!isServiceMode){
      runningBuild.getBuildLogger().debug("Swabra is enabled even though it is only supported in service mode.");
    }
    final Collection<AgentBuildFeature> features = runningBuild.getBuildFeaturesOfType(SwabraUtil.FEATURE_TYPE);

    final Map<String, String> params = new HashMap<String, String>();
    if (!features.isEmpty()) {
      params.putAll(features.iterator().next().getParameters());
    }
    params.putAll(runningBuild.getSharedConfigParameters());

    myCleanupEnabled = isServiceMode && SwabraUtil.isCleanupEnabled(params);
    myCleanupMode = SwabraUtil.getCleanupMode(params);
    myStrict = SwabraUtil.isStrict(params);
    myLockingProcessesKill = SystemInfo.isWindows && SwabraUtil.isLockingProcessesKill(params);
    myLockingProcessesReport = SystemInfo.isWindows && SwabraUtil.isLockingProcessesReport(params);
    myVerbose = SwabraUtil.isVerbose(params);
    myCheckoutDir = runningBuild.getCheckoutDirectory();

    final List<String> rules = new ArrayList<String>();
    rules.addAll(SwabraUtil.splitRules(SwabraUtil.getRules(params)));

    if (params.containsKey(DEFAULT_RULES_CONFIG_PARAM)) {
      rules.addAll(SwabraUtil.splitRules(params.get(DEFAULT_RULES_CONFIG_PARAM)));
    } else if (AgentCheckoutMode.ON_AGENT == runningBuild.getEffectiveCheckoutMode()) {
      rules.addAll(Arrays.asList(DEFAULT_RULES));
    }
    myRules = new SwabraRules(myCheckoutDir, rules);
    myIgnoredProcesses = getIgnoredProcesses(params.get(IGNORED_PPRCESSES_CONFIG_PARAM));

    logSettings();
  }

  public void setCleanupEnabled(boolean cleanupEnabled) {
    myCleanupEnabled = cleanupEnabled;
  }

  public boolean isCleanupEnabled() {
    return myCleanupEnabled;
  }

  public boolean isSwabraEnabled(){
    return isCleanupEnabled() || isLockingProcessesDetectionEnabled();
  }

  public boolean isCleanupBeforeBuild() {
    return myCleanupEnabled && !isCleanupAfterBuild();
  }

  public boolean isCleanupAfterBuild() {
    return myCleanupEnabled && SwabraUtil.isAfterBuildCleanup(myCleanupMode);
  }

  public boolean isStrict() {
    return myStrict;
  }

  public boolean isLockingProcessesKill() {
    return myLockingProcessesKill;
  }

  public boolean isLockingProcessesDetectionEnabled() {
    return (myLockingProcessesKill || myLockingProcessesReport) && myHandlePath != null;
  }

  @Nullable
  public String getHandlePath() {
    return myHandlePath == null ? null : myHandlePath.getPath();
  }

  public SwabraRules getRules() {
    return myRules;
  }

  public File getCheckoutDir() {
    return myCheckoutDir;
  }

  public boolean isVerbose() {
    return myVerbose;
  }

  private void logSettings() {
    SwabraLogger.CLASS_LOGGER.debug("Swabra settings: " +
      "cleanup mode = '" + myCleanupMode +
      "', strict = '" + myStrict +
      "', locking processes kill = '" + myLockingProcessesKill +
      "', locking processes report = '" + myLockingProcessesReport +
      "', verbose = '" + myVerbose + "'.");
  }

  public void prepareHandle(SwabraLogger logger, @NotNull BundledToolsRegistry toolsRegistry) {
    if (myLockingProcessesKill || myLockingProcessesReport) {
      final HandlePathProvider handlePathProvider = new HandlePathProvider(logger, toolsRegistry.findTool("SysinternalsHandle"));
      myHandlePath = handlePathProvider.getHandlePath();
    } else {
      myHandlePath = null;
    }
  }

  /**
   * @return processes that should never be killed in case they lock a file
   */
  @NotNull
  public List<String> getIgnoredProcesses() {
    return myIgnoredProcesses;
  }


  @NotNull
  private List<String> getIgnoredProcesses(@Nullable String pidsStr) {
    if (StringUtil.isEmptyOrSpaces(pidsStr)) return Collections.emptyList();

    final List<String> res = new ArrayList<String>();
    for (String pid : pidsStr.split(" *[,;\n\r] *")) {
      if (StringUtil.isNotEmpty(pid)) res.add(pid);
    }
    return res;
  }
}