package jetbrains.buildServer.swabra.processes;

import java.io.File;
import jetbrains.buildServer.agent.BundledTool;
import jetbrains.buildServer.swabra.SwabraLogger;
import jetbrains.buildServer.swabra.SwabraUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 *         Date: 20.04.11 17:12
 */
public class HandlePathProvider {
  private static final String HANDLE_PATH_SUFFIX = File.separator + "handle.exe";

  private final File myHandlePath;

  @Nullable
  public File getHandlePath() {
    return myHandlePath;
  }

  public HandlePathProvider(@NotNull SwabraLogger logger, @Nullable final BundledTool handleTool) {
    myHandlePath = findHandlePath(logger, handleTool);
  }

  @Nullable
  private static File findHandlePath(@NotNull SwabraLogger logger, @Nullable final BundledTool handleTool) {
    String path = handleTool == null ? null : getHandlePath(handleTool);
    if (StringUtil.isEmptyOrSpaces(path)) {
      logDetectionDisabled("Path to handle.exe tool is not defined. Use Swabra settings to install handle.exe", logger);
      return null;
    }
    if (!SwabraUtil.unifyPath(path).endsWith(HANDLE_PATH_SUFFIX)) {
      logDetectionDisabled("Path to handle.exe tool must end with: " + HANDLE_PATH_SUFFIX, logger);
      return null;
    }
    final File handleFile = new File(path);
    if (!handleFile.isFile()) {
      logDetectionDisabled("No executable found at " + path, logger);
      return null;
    }

    return handleFile;
  }


  private static void logDetectionDisabled(@NotNull String details, @NotNull SwabraLogger logger) {
   logger.warn("Disabling locking processes detection. " + details);
  }

  @NotNull
  private static String getHandlePath(@NotNull BundledTool handleToolPath) {
    return new File(handleToolPath.getRootPath(), "handle.exe").getPath();
  }
}
