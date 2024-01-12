

package jetbrains.buildServer.swabra.processes;

import jetbrains.buildServer.ExecResult;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @User Victory.Bedrosova
 * 3/25/14.
 */
public class WmicProcessDetailsProvider {
  private static final Logger LOG = Logger.getLogger(LockedFileResolver.class);

  /**
   * Returns "wmic process <pid>" command output for the specified process id or null in case such command failed or returned no result
   * @param pid process to get details for
   * @return see above
   */
  @Nullable
  public String getProcessDetails(@NotNull Long pid) {
    try {
      final ExecResult result = ProcessExecutor.runWmicProcess(pid);

      final String stdout = result.getStdout();
      LOG.debug("wmic output:\n" + stdout);

      return noDetailsAvailable(stdout) ? null : stdout;

    } catch (Exception e) {
      LOG.warn(e.getMessage(), e);
      return null;
    }
  }

  private boolean noDetailsAvailable(final String stdout) {
    return stdout.contains("No Instance(s) Available.") ||  stdout.contains("ERROR");
  }
}