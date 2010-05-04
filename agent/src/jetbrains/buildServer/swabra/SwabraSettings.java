package jetbrains.buildServer.swabra;

import java.io.File;
import java.util.Map;

/**
 * User: vbedrosova
 * Date: 04.05.2010
 * Time: 19:04:35
 */
public class SwabraSettings {
  private static final String HANDLE_PATH_SUFFIX = File.separator + "handle.exe";
  private static final String HANDLE_EXE_SYSTEM_PROP = "handle.exe.path";

  private boolean myCleanupEnabled;
  private final boolean myStrict;

  private final boolean myLockingProcessesKill;
  private final boolean myLockingProcessesReport;

  private final boolean myVerbose;

  private String myHandlePath;

  public SwabraSettings(Map<String, String> runnerParams, SwabraLogger logger) {
    myCleanupEnabled = SwabraUtil.isCleanupEnabled(runnerParams);
    myStrict = SwabraUtil.isStrict(runnerParams);
    myLockingProcessesKill = SwabraUtil.isLockingProcessesKill(runnerParams);
    myLockingProcessesReport = SwabraUtil.isLockingProcessesReport(runnerParams);
    myVerbose = SwabraUtil.isVerbose(runnerParams);
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

  public boolean isVerbose() {
    return myVerbose;
  }

  private void logSettings() {
    SwabraLogger.CLASS_LOGGER.debug("Swabra settings: " +
      "cleanup enabled = '" + myCleanupEnabled +
      "', strict = " + myStrict +
      "', locking processes kill = " + myLockingProcessesKill +
      "', locking processes report = " + myLockingProcessesReport +
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
}
