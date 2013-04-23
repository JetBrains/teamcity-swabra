package jetbrains.buildServer.swabra.serverHealth;

import java.util.Map;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.swabra.SwabraBuildFeature;
import jetbrains.buildServer.swabra.SwabraUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Victory.Bedrosova
 * Date: 4/19/13
 * Time: 5:59 PM
 */
public class SwabraSettings {
  private final boolean myFeaturePresent;
  private final boolean myCleanupEnabled;
  private final boolean myStrict;

  public SwabraSettings(@NotNull SBuildType bt) {
    final SBuildFeatureDescriptor feature = getSwabraBuildFeature(bt);
    myFeaturePresent = feature != null;
    if (myFeaturePresent) {
      final Map<String, String> parameters = feature.getParameters();

      myCleanupEnabled = SwabraUtil.isCleanupEnabled(parameters);
      myStrict = SwabraUtil.isStrict(parameters);
    } else {
      myCleanupEnabled = false;
      myStrict = false;
    }
  }

  @Used("jsp")
  public boolean isCleanupEnabled() {
    return myCleanupEnabled;
  }

  @Used("jsp")
  public boolean isStrict() {
    return myStrict;
  }

  @Used("jsp")
  public boolean isFeaturePresent() {
    return myFeaturePresent;
  }

  private boolean isActualCleanupEnabled() {
    return myFeaturePresent && myCleanupEnabled;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final SwabraSettings that = (SwabraSettings)o;

    if (isActualCleanupEnabled() != that.isActualCleanupEnabled()) return false;
    if (myStrict != that.myStrict) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (isActualCleanupEnabled() ? 1 : 0);
    result = 31 * result + (myStrict ? 1 : 0);
    return result;
  }

  @Nullable
  private static SBuildFeatureDescriptor getSwabraBuildFeature(@NotNull SBuildType bt) {
    for (SBuildFeatureDescriptor feature : bt.getBuildFeatures()) {
      if (SwabraBuildFeature.FEATURE_TYPE.equals(feature.getType()) && bt.isEnabled(feature.getId())) {
       return feature;
      }
    }
    return null;
  }
}