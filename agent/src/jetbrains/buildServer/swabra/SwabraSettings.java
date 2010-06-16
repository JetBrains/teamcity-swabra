package jetbrains.buildServer.swabra;

import jetbrains.buildServer.agent.AgentRunningBuild;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * User: vbedrosova
 * Date: 04.05.2010
 * Time: 19:04:35
 */
public class SwabraSettings {
  private static final String RULES_PROPERTY = "swabra.rules";

  private static final String[] DEFAULT_EXCLUDE_RULES = {"-:**/.svn/**", "-:**/.git/**", "-:**/.hg/**", "-:**/CVS/**"};

  private static final String HANDLE_PATH_SUFFIX = File.separator + "handle.exe";
  private static final String HANDLE_EXE_SYSTEM_PROP = "handle.exe.path";

  private boolean myCleanupEnabled;
  private final boolean myStrict;

  private final boolean myLockingProcessesKill;
  private final boolean myLockingProcessesReport;

  private final boolean myVerbose;

  private String myHandlePath;

  private final List<String> myRules;

  private final File myCheckoutDir;


  public SwabraSettings(AgentRunningBuild runningBuild, SwabraLogger logger) {
    final Map<String, String> runnerParams = runningBuild.getRunnerParameters();
    myCleanupEnabled = SwabraUtil.isCleanupEnabled(runnerParams);
    myStrict = SwabraUtil.isStrict(runnerParams);
    myLockingProcessesKill = SwabraUtil.isLockingProcessesKill(runnerParams);
    myLockingProcessesReport = SwabraUtil.isLockingProcessesReport(runnerParams);
    myVerbose = SwabraUtil.isVerbose(runnerParams);
    myCheckoutDir = runningBuild.getCheckoutDirectory();

    final Map<String, String> systemProperties = runningBuild.getBuildParameters().getSystemProperties();
    myRules = new ArrayList<String>();
    myRules.addAll(splitRules(SwabraUtil.getRules(runnerParams)));
    if (systemProperties.containsKey(RULES_PROPERTY)) {
      myRules.addAll(splitRules(systemProperties.get(RULES_PROPERTY)));
    } else if (runningBuild.isCheckoutOnAgent()) {
      myRules.addAll(Arrays.asList(DEFAULT_EXCLUDE_RULES));
    }

    logSettings();
    prepareHandle(logger);
  }

  public void setCleanupEnabled(boolean cleanupEnabled) {
    myCleanupEnabled = cleanupEnabled;
  }

  public boolean isCleanupEnabled() {
    return myCleanupEnabled;
  }

  public boolean isStrict() {
    return myStrict;
  }

  public boolean isLockingProcessesKill() {
    return myLockingProcessesKill;
  }

  public boolean isLockingProcessesReport() {
    return myLockingProcessesReport;
  }

  public boolean isLockingProcessesDetectionEnabled() {
    return (myLockingProcessesKill || myLockingProcessesReport) && myHandlePath != null;
  }

  public String getHandlePath() {
    return myHandlePath;
  }

  public List<String> getRules() {
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
      "cleanup enabled = '" + myCleanupEnabled +
      "', strict = '" + myStrict +
      "', locking processes kill = '" + myLockingProcessesKill +
      "', locking processes report = '" + myLockingProcessesReport +
      "', verbose = '" + myVerbose + "'.");
  }

  private void prepareHandle(SwabraLogger logger) {
    if (myLockingProcessesKill || myLockingProcessesReport) {
      myHandlePath = System.getProperty(HANDLE_EXE_SYSTEM_PROP);
      if (notDefined(myHandlePath)) {
        logger.swabraWarn("Handle path not defined");
        myHandlePath = null;
        return;
      }
      if (!SwabraUtil.unifyPath(myHandlePath).endsWith(HANDLE_PATH_SUFFIX)) {
        logger.swabraWarn("Handle path must end with: " + HANDLE_PATH_SUFFIX);
        myHandlePath = null;
        return;
      }
      final File handleFile = new File(myHandlePath);
      if (!handleFile.isFile()) {
        logger.swabraWarn("No Handle executable found at " + myHandlePath);
        myHandlePath = null;
      }
    } else {
      myHandlePath = null;
    }
  }

  private static boolean notDefined(String value) {
    return (value == null) || ("".equals(value));
  }

  private static List<String> splitRules(String rules) {
    return Arrays.asList(rules.split(" *[,\n\r] *"));
  }
}
