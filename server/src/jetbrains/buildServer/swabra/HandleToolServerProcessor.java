package jetbrains.buildServer.swabra;

import java.io.File;
import java.io.IOException;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.tools.ServerToolProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 26.04.11
 * Time: 22:10
 */
public class HandleToolServerProcessor implements ServerToolProcessor {
  @NotNull
  private final HandleProvider myHandleProvider;

  public HandleToolServerProcessor(@NotNull final HandleProvider handleProvider) {
    myHandleProvider = handleProvider;
  }

  @NotNull
  public String getType() {
    return "handleTool";
  }

  public void processTool(@NotNull final File tool, @NotNull final ServerToolProcessorCallback callback) {
    try {
      myHandleProvider.packPlugin(tool);
      callback.progress("Created agent plugin at " + myHandleProvider.getPluginFolder(), Status.NORMAL);
      callback.progress("handle.exe will be present on agents after the upgrade process (will start automatically)", Status.NORMAL);
    } catch (Throwable throwable) {
      callback.progress("Failed to create agent plugin at " + myHandleProvider.getPluginFolder() + ", please see teamcity-server.log for details", Status.ERROR);
    }
  }
}
