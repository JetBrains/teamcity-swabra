package jetbrains.buildServer.swabra;

import jetbrains.buildServer.agent.AgentRunningBuild;

import java.io.File;
import java.util.*;

/**
 * User: vbedrosova
 * Date: 04.05.2010
 * Time: 19:04:35
 */
public class SwabraSettings {
  private static final String IGNORED_PATHS_PROPERTY = "swabra.ignored.paths";

  private static final String[] DEFAULT_IGNORED_PATHS = {"**/.svn", "**/.git", "**/.hg", "**/CVS"};

  private static final String HANDLE_PATH_SUFFIX = File.separator + "handle.exe";
  private static final String HANDLE_EXE_SYSTEM_PROP = "handle.exe.path";

  private boolean myCleanupEnabled;
  private final boolean myStrict;

  private final boolean myLockingProcessesKill;
  private final boolean myLockingProcessesReport;

  private final boolean myVerbose;

  private String myHandlePath;

  private final Set<String> myIgnoredPaths;

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
    myIgnoredPaths = new HashSet<String>();
    if (systemProperties.containsKey(IGNORED_PATHS_PROPERTY)) {
      myIgnoredPaths.addAll(splitIgnoredPaths(systemProperties.get(IGNORED_PATHS_PROPERTY)));
    } else if (runningBuild.isCheckoutOnAgent()) {
      myIgnoredPaths.addAll(Arrays.asList(DEFAULT_IGNORED_PATHS));
    }
    myIgnoredPaths.addAll(splitIgnoredPaths(SwabraUtil.getIgnored(runnerParams)));

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

  public Set<String> getIgnoredPaths() {
    return myIgnoredPaths;
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

  private static List<String> splitIgnoredPaths(String ignoredPathsStr) {
    return Arrays.asList(ignoredPathsStr.split(" *[,\n\r] *"));
  }
}
