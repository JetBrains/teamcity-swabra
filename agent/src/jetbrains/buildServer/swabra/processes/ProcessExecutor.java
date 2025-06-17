

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

  public static ExecResult runWmicProcess(@NotNull Long pid) {
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath("wmic");
    commandLine.addParameter("process");
    commandLine.addParameter(String.valueOf(pid));

    return run(commandLine);
  }

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

}