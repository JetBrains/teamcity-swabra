

package jetbrains.buildServer.swabra.processes;

import com.intellij.execution.configurations.GeneralCommandLine;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 01.12.2009
 * Time: 11:35:14
 */
public class ProcessExecutor {
  private static final Logger LOG = Logger.getLogger(ProcessExecutor.class);

  private static final int TIMEOUT = 1000;
  private static final String ACCEPT_EULA_KEY = "/accepteula";

  public static ExecResult runHandleAcceptEula(@NotNull final String handleExePath, @NotNull String file) {
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(handleExePath);
    commandLine.addParameter(ACCEPT_EULA_KEY);
    commandLine.addParameter(file);

    return run(commandLine);
  }

  public static ExecResult runWmicProcess(@NotNull Long pid) {
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath("wmic");
    commandLine.addParameter("process");
    commandLine.addParameter(String.valueOf(pid));

    return run(commandLine);
  }

//  public static ExecResult run(@NotNull String exePath, @NotNull String[] params, SimpleBuildLogger logger) {
//    final GeneralCommandLine commandLine = new GeneralCommandLine();
//    commandLine.setExePath(exePath);
//    commandLine.addParameters(params);
//
//    return run(commandLine, logger);
//  }

  private static ExecResult run(final GeneralCommandLine commandLine) {
    return SimpleCommandLineProcessRunner.runCommand(commandLine, new byte[0], new SimpleCommandLineProcessRunner.ProcessRunCallback() {
      @Override
      public void onProcessStarted(Process ps) {
        LOG.debug("Started " + commandLine.getCommandLineString());
      }
      @Override
      public void onProcessFinished(Process ps) {
        LOG.debug("Finished " + commandLine.getCommandLineString());
      }
      @Override
      public Integer getOutputIdleSecondsTimeout() {
        return TIMEOUT;
      }
      @Override
      public Integer getMaxAcceptedOutputSize() {
        return null;
      }
      @Override
      public boolean terminateEntireProcessTree() {
        return false;
      }
    });
  }

//  private static void info(String message, SimpleBuildLogger logger) {
//    LOG.info(message);
//    if (logger != null) {
//      logger.message(message);
//    }
//  }
//
//  private static void error(String message, SimpleBuildLogger logger) {
//    LOG.error(message);
//    if (logger != null) {
//      logger.error(message);
//    }
//  }
}