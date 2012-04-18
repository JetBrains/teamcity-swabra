/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BundledToolsRegistry;
import jetbrains.buildServer.swabra.processes.HandlePathProvider;
import jetbrains.buildServer.swabra.snapshots.SwabraRules;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 04.05.2010
 * Time: 19:04:35
 */
public class SwabraSettings {
  private static final String DEFAULT_RULES_CONFIG_PARAM = "swabra.default.rules";

  private static final String[] DEFAULT_RULES = {"-:**/.svn", "-:**/.git", "-:**/.hg", "-:**/CVS", "-:.svn", "-:.git", "-:.hg", "-:CVS"};


  private boolean myCleanupEnabled;
  private final String myCleanupMode;
  private final boolean myStrict;

  private final boolean myLockingProcessesKill;
  private final boolean myLockingProcessesReport;

  private final boolean myVerbose;

  private File myHandlePath;

  private final SwabraRules myRules;

  private final File myCheckoutDir;


  public SwabraSettings(AgentRunningBuild runningBuild) {
    final Map<String, String> params = runningBuild.getSharedConfigParameters();
    myCleanupEnabled = SwabraUtil.isCleanupEnabled(params);
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
    } else if (runningBuild.isCheckoutOnAgent()) {
      rules.addAll(Arrays.asList(DEFAULT_RULES));
    }
    myRules = new SwabraRules(myCheckoutDir, rules);

    logSettings();
  }

  public void setCleanupEnabled(boolean cleanupEnabled) {
    myCleanupEnabled = cleanupEnabled;
  }

  public boolean isCleanupEnabled() {
    return myCleanupEnabled;
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

  public String getHandlePath() {
    return myHandlePath.getPath();
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
}
