

package jetbrains.buildServer.swabra.serverHealth;

import java.util.Collection;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;

/**
 * @author vbedrosova
 */
public interface SwabraCleanCheckoutWatcher {
  // returns ids of the build types which recently caused swabra clean checkout for the provided build type
  @NotNull
  public Collection<String> getRecentCleanCheckoutCauses(@NotNull SBuildType buildType);
}