/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.swabra.serverHealth;

import java.util.*;
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

  @NotNull private final SwabraCleanCheckoutWatcher myWatcher;
  @NotNull private final ProjectManager myProjectManager;

  public SwabraClashingConfigurationsDetector(@NotNull final SwabraCleanCheckoutWatcher watcher, @NotNull final ProjectManager projectManager) {
    myWatcher = watcher;
    myProjectManager = projectManager;
  }

  @NotNull
  public List<List<SwabraSettingsGroup>> getClashingConfigurationsGroups(@NotNull Collection<SBuildType> buildTypes) {
    final List<List<SwabraSettingsGroup>> res = new ArrayList<List<SwabraSettingsGroup>>();
    for (Collection<SBuildType> group : groupClashingBuildTypes(buildTypes)) {
      if (group.size() > 1) {
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

  @NotNull
  private Collection<Collection<SBuildType>> groupClashingBuildTypes(@NotNull Collection<SBuildType> buildTypes) {
    final List<ClashingGroup> groups = new ArrayList<ClashingGroup>();
    for (SBuildType bt : buildTypes) {
      final Collection<String> cleanCheckoutCauses = myWatcher.getRecentCleanCheckoutCauses(bt);
      if (cleanCheckoutCauses.isEmpty()) continue;

      ClashingGroup relatedGroup = null;
      for (ClashingGroup group : groups) {
        if (null != CollectionsUtil.findFirst(group.guiltyBuildTypes, new Filter<String>() {
          @Override
          public boolean accept(@NotNull final String data) {
            return cleanCheckoutCauses.contains(data);
          }
        })) {
          relatedGroup = group;
          break;
        };
      }
      if (relatedGroup == null) {
        relatedGroup = new ClashingGroup();
        groups.add(relatedGroup);
      }
      relatedGroup.sufferingBuildTypes.add(bt.getBuildTypeId());
      relatedGroup.guiltyBuildTypes.addAll(cleanCheckoutCauses);
    }
    return CollectionsUtil.convertCollection(groups, new Converter<Collection<SBuildType>, ClashingGroup>() {
      @Override
      public Collection<SBuildType> createFrom(@NotNull final ClashingGroup source) {
        final Set<SBuildType> res = new HashSet<SBuildType>();
        res.addAll(getBuildTypes(source.sufferingBuildTypes));
        res.addAll(getBuildTypes(source.guiltyBuildTypes));
        return res;
      }
    });
  }

  @NotNull
  private Collection<SBuildType> getBuildTypes(@NotNull Collection<String> ids) {
    return CollectionsUtil.convertAndFilterNulls(ids, new Converter<SBuildType, String>() {
      @Override
      public SBuildType createFrom(@NotNull final String source) {
        return myProjectManager.findBuildTypeById(source);
      }
    });
  }

  private static final class ClashingGroup {
    private final Set<String> sufferingBuildTypes = new HashSet<>();
    private final Set<String> guiltyBuildTypes = new HashSet<>();
  }
}
