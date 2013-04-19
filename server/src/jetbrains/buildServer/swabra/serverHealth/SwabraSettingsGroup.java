package jetbrains.buildServer.swabra.serverHealth;

import java.util.List;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;

/**
 * User: Victory.Bedrosova
 * Date: 4/19/13
 * Time: 6:00 PM
 */
public final class SwabraSettingsGroup {
  @NotNull private final SwabraSettings mySettings;
  @NotNull private final List<SBuildType> myBuildTypes;

  public SwabraSettingsGroup(@NotNull final SwabraSettings settings,
                             @NotNull final List<SBuildType> buildTypes) {
    mySettings = settings;
    myBuildTypes = buildTypes;
  }


  @Used("jsp")
  @NotNull
  public SwabraSettings getSettings() {
    return mySettings;
  }

  @Used("jsp")
  @NotNull
  public List<SBuildType> getBuildTypes() {
    return myBuildTypes;
  }
}
