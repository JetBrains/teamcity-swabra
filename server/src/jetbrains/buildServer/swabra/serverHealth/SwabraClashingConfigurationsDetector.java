package jetbrains.buildServer.swabra.serverHealth;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.swabra.SwabraBuildFeature;
import jetbrains.buildServer.swabra.SwabraUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Victory.Bedrosova
 * Date: 4/15/13
 * Time: 6:08 PM
 */
public class SwabraClashingConfigurationsDetector {
  @NotNull
  public List<List<SBuildType>> getClashingConfigurations(@NotNull Collection<SBuildType> buildTypes) {
    final List<List<SBuildType>> res = new ArrayList<List<SBuildType>>();
    for (Collection<SBuildType> group : groupBuildTypesByCheckoutDir(buildTypes)) {
      if (group.size() > 1) {
        final Map<SwabraSettings, List<SBuildType>> clashed = CollectionsUtil.groupBy(group, new Converter<SwabraSettings, SBuildType>() {
          public SwabraSettings createFrom(@NotNull final SBuildType bt) {
            return new SwabraSettings(bt);
          }
        });

        if (clashed.size() > 1) {
          res.add(new ArrayList<SBuildType>(group));
        }
      }
    }
    return res;
  }

  /**
   * Returns build types with enabled Swabra build feature grouped by vcs settings hash or by custom checkout dir
   * @return
   */
  @NotNull
  private Collection<Collection<SBuildType>> groupBuildTypesByCheckoutDir(@NotNull Collection<SBuildType> buildTypes) {
    final Map<String, Collection<SBuildType>> res = new HashMap<String, Collection<SBuildType>>();
    for (SBuildType bt : buildTypes) {
      Collection<SBuildType> bts;

      String checkotDir = bt.getCheckoutDirectory();
      if (checkotDir != null) {
        checkotDir = ReferencesResolverUtil.isReference(checkotDir) ? bt.getResolvedSettings().getCheckoutDirectory() : checkotDir;
      }

      final String groupKey = StringUtil.isEmptyOrSpaces(checkotDir) ? bt.getVcsSettingsHash() : checkotDir;

      bts = res.get(groupKey);
      if (bts != null) {
        bts.add(bt);
      } else {
        bts = new ArrayList<SBuildType>();
        bts.add(bt);
        res.put(checkotDir == null ? groupKey : checkotDir, bts);
      }
    }
    return res.values();
  }

  private static final class SwabraSettings {
    private final boolean featurePresent;
    private final boolean cleaupEnabled;
    private final boolean strict;

    private SwabraSettings(@NotNull SBuildType bt) {
      final SBuildFeatureDescriptor feature = getSwabtaBuildFeature(bt);
      featurePresent = feature != null;
      if (featurePresent) {
        final Map<String, String> parameters = feature.getParameters();

        cleaupEnabled = SwabraUtil.isCleanupEnabled(parameters);
        strict = SwabraUtil.isStrict(parameters);
      } else {
        cleaupEnabled = false;
        strict = false;
      }
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final SwabraSettings that = (SwabraSettings)o;

      if (cleaupEnabled != that.cleaupEnabled) return false;
      if (featurePresent != that.featurePresent) return false;
      if (strict != that.strict) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = (featurePresent ? 1 : 0);
      result = 31 * result + (cleaupEnabled ? 1 : 0);
      result = 31 * result + (strict ? 1 : 0);
      return result;
    }

    @Nullable
    private static SBuildFeatureDescriptor getSwabtaBuildFeature(@NotNull SBuildType bt) {
      for (SBuildFeatureDescriptor feature : bt.getBuildFeatures()) {
        if (SwabraBuildFeature.FEATURE_TYPE.equals(feature.getType()) && bt.isEnabled(feature.getId())) {
         return feature;
        }
      }
      return null;
    }
  }
}
