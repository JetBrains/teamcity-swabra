package jetbrains.buildServer.swabra;

import java.util.Collection;
import java.util.Map;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

public class SwabraBuildFacility extends BuildFacility implements BuildStartContextProcessor {
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

  public void updateParameters(@NotNull BuildStartContext context) {
    SBuildType buildType = context.getBuild().getBuildType();
    if (buildType == null) return;
    Collection<SBuildFacilityDescriptor> buildFacilities = buildType.getBuildFacilities();
    for (SBuildFacilityDescriptor bf: buildFacilities) {
      if (bf.getType().equals(getType())) {
        for (final Map.Entry<String, String> param : bf.getParameters().entrySet()) {
          if (param.getValue() != null) {
            context.addSharedParameter(param.getKey(), param.getValue());
          }
        }
      }
    }
  }

  @Override
  public boolean isMultipleFacilitiesPerBuildTypeAllowed() {
    return false;
  }

  @NotNull
  @Override
  public String describeParameters(@NotNull final Map<String, String> params) {
    StringBuilder result = new StringBuilder();
    if (SwabraUtil.isCleanupEnabled(params)) {
      result.append("Build files cleanup enabled\n");
    } else {
      result.append("Build files cleanup disabled\n");
    }
    if (SwabraUtil.isLockingProcessesReport(params)) {
      result.append("Will report about processes locking checkout directory\n");
    }
    if (SwabraUtil.isLockingProcessesKill(params)) {
      result.append("Will try to kill processes locking checkout directory\n");
    }
    return result.toString();
  }
}
