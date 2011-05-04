package jetbrains.buildServer.swabra;

import jetbrains.buildServer.tools.ToolTypeExtension;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 25.04.11
 * Time: 16:38
 */
public class HandleTool extends ToolTypeExtension {
  @NotNull
  public String getType() {
    return "handleTool";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return "Sysinternals handle.exe";
  }

  @Override
  public String getDescription() {
    return "On Windows agents handle.exe is used to determine processes which hold files in the checkout directory.<br/>Note that by " +
           "pressing \"Load\" button you accept the <a showdiscardchangesmessage=\"false\" target=\"_blank\" " +
           "href=\"http://technet.microsoft.com/en-us/sysinternals/bb469936.aspx\">Sysinternals Software License Terms</a>.<br/> " +
           "handle.exe will be automatically distributed to the agents and the agents will restart.";
  }

  @Override
  public String getFileName() {
    return "handle.exe";
  }

  @Override
  public boolean isSupportDownload() {
    return true;
  }

  @Override
  public String getDownloadUrl() {
    return "http://live.sysinternals.com/handle.exe";
  }

  @Override
  public String getToolSiteUrl() {
    return "http://technet.microsoft.com/en-us/sysinternals/bb469936.aspx";
  }
}
