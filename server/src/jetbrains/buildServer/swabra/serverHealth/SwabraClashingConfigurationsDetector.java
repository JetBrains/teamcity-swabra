package jetbrains.buildServer.swabra.serverHealth;

import com.intellij.openapi.util.text.StringUtil;
import java.util.*;
import jetbrains.buildServer.parameters.ReferencesResolverUtil;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;

/**
 * User: Victory.Bedrosova
 * Date: 4/15/13
 * Time: 6:08 PM
 */
public class SwabraClashingConfigurationsDetector {
  @NotNull
  public List<List<SwabraSettingsGroup>> getClashingConfigurationsGroups(@NotNull Collection<SBuildType> buildTypes, @NotNull Collection<SBuildType> scopeBuildTypes) {
    final List<List<SwabraSettingsGroup>> res = new ArrayList<List<SwabraSettingsGroup>>();
    for (Collection<SBuildType> group : groupBuildTypesByCheckoutDir(buildTypes)) {
      if (group.size() > 1 && buildTypesAccepted(group, scopeBuildTypes)) {
        final Map<SwabraSettings, List<SBuildType>> clashed = CollectionsUtil.groupBy(group, new Converter<SwabraSettings, SBuildType>() {
          public SwabraSettings createFrom(@NotNull final SBuildType bt) {
            return new SwabraSettings(bt);
          }
        });

        if (clashed.size() > 1) {
          res.add(CollectionsUtil
                    .convertCollection(clashed.entrySet(), new Converter<SwabraSettingsGroup, Map.Entry<SwabraSettings, List<SBuildType>>>() {
                      public SwabraSettingsGroup createFrom(@NotNull final Map.Entry<SwabraSettings, List<SBuildType>> source) {
                        return new SwabraSettingsGroup(source.getKey(), source.getValue());
                      }
                    }));
        }
      }
    }
    return res;
  }

  private static boolean buildTypesAccepted(@NotNull Collection<SBuildType> buildTypes, @NotNull final Collection<SBuildType> scopeBuildTypes) {
    return null != CollectionsUtil.findFirst(buildTypes, new Filter<SBuildType>() {
      public boolean accept(@NotNull final SBuildType data) {
        return scopeBuildTypes.contains(data);
      }
    });
  }

  @NotNull
  public List<List<SBuildType>> getClashingConfigurations(@NotNull Collection<SBuildType> buildTypes, @NotNull Collection<SBuildType> scopeBuildTypes) {
    final List<List<SBuildType>> res = new ArrayList<List<SBuildType>>();
    for (List<SwabraSettingsGroup> groups : getClashingConfigurationsGroups(buildTypes, scopeBuildTypes)) {
      final List<SBuildType> tmpRes = new ArrayList<SBuildType>();
      for (SwabraSettingsGroup group: groups) tmpRes.addAll(group.getBuildTypes());
      res.add(tmpRes);
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
}
