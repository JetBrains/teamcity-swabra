package jetbrains.buildServer.swabra;

import jetbrains.buildServer.serverSide.BuildFacility;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

public class SwabraBuildFacility extends BuildFacility {
  private final String myEditUrl;

  public SwabraBuildFacility(@NotNull final PluginDescriptor descriptor) {
    myEditUrl = descriptor.getPluginResourcesPath("swabraSettings.jsp");
  }

  @NotNull
  @Override
  public String getType() {
    return "swabra";
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Swabra";
  }

  @Override
  public String getEditParametersUrl() {
    return myEditUrl;
  }
}
