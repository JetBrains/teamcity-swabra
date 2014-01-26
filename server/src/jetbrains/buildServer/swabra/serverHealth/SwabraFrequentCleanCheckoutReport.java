/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.healthStatus.*;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import jetbrains.buildServer.web.util.SessionUser;
import org.jetbrains.annotations.NotNull;

/**
 * User: Victory.Bedrosova
 * Date: 4/15/13
 * Time: 5:35 PM
 *
 * Detects frequent clean checkout cause - build types working in the same checkout directory and having different Swabra settings
 */
public class SwabraFrequentCleanCheckoutReport extends HealthStatusReport {
  private static final String SWABRA_FREQUENT_CLEAN_CHECKOUT_TYPE = "swabraFrequentCleanCheckout";
  public static final String SWABRA_CLASHING_BUILD_TYPES = "swabraClashingBuildTypes";
  public static final String CATEGORY_NAME = "Possible frequent clean checkout (Swabra case)";
  public static final String CATEGORY_DESCRIPTION = "Build configurations with the same checkout directory but different Swabra settings";

  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final ItemCategory myCategory;

  public SwabraFrequentCleanCheckoutReport(@NotNull final PluginDescriptor descriptor,
                                           @NotNull final PagePlaces pagePlaces,
                                           @NotNull final WebLinks webLinks,
                                           @NotNull ProjectManager projectManager) {
    final HealthStatusItemPageExtension pageExtension = new HealthStatusItemPageExtension(SWABRA_FREQUENT_CLEAN_CHECKOUT_TYPE, pagePlaces) {
      @Override
      public boolean isAvailable(@NotNull final HttpServletRequest request) {
        if (super.isAvailable(request)) {
          final SUser user = SessionUser.getUser(request);
          if (user != null) {
            final HealthStatusItem item = getStatusItem(request);
            //noinspection unchecked
            for (SBuildType bt : getBuildTypes((List<SwabraSettingsGroup>)item.getAdditionalData().get(SWABRA_CLASHING_BUILD_TYPES)) ){
              if (user.getPermissionsGrantedForProject(bt.getProjectId()).contains(Permission.EDIT_PROJECT)) {
                return true;
              }
            }
          }
        }
        return false;
      }
    };

    pageExtension.setIncludeUrl(descriptor.getPluginResourcesPath("swabraClashingBuildTypes.jsp"));
    pageExtension.setVisibleOutsideAdminArea(true);
    pageExtension.register();

    myCategory = new ItemCategory(SWABRA_FREQUENT_CLEAN_CHECKOUT_TYPE, CATEGORY_NAME, ItemSeverity.INFO, CATEGORY_DESCRIPTION, webLinks.getHelp("Build Files Cleaner (Swabra)"));
    myProjectManager = projectManager;
  }

  @NotNull
  @Override
  public String getType() {
    return SWABRA_FREQUENT_CLEAN_CHECKOUT_TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Detect Swabra settings which may cause frequent clean checkout";
  }

  @NotNull
  @Override
  public Collection<ItemCategory> getCategories() {
    return Collections.singleton(myCategory);
  }

  @Override
  public void report(@NotNull final HealthStatusScope scope, @NotNull final HealthStatusItemConsumer resultConsumer) {
    if (!scope.isItemWithSeverityAccepted(ItemSeverity.INFO) || scope.getBuildTypes().isEmpty()) return;

    final List<List<SwabraSettingsGroup>> result =
      new SwabraClashingConfigurationsDetector().getClashingConfigurationsGroups(myProjectManager.getActiveBuildTypes(), scope.getBuildTypes());

    for (List<SwabraSettingsGroup> group : result) {
      if(group.isEmpty()) continue;

      group = new ArrayList<SwabraSettingsGroup>(group);

      Collections.sort(group, new Comparator<SwabraSettingsGroup>() {
        public int compare(final SwabraSettingsGroup o1, final SwabraSettingsGroup o2) {
          return o1.getBuildTypes().size() - o2.getBuildTypes().size();
        }
      });

      final HealthStatusItem item =
        new HealthStatusItem(signature(group), myCategory, Collections.<String, Object>singletonMap(SWABRA_CLASHING_BUILD_TYPES, group));

      for(SBuildType affectedBuildType: getBuildTypes(group)) {
        resultConsumer.consumeForBuildType(affectedBuildType, item);
      }
    }
  }

  @NotNull
  private List<SBuildType> getBuildTypes(@NotNull List<SwabraSettingsGroup> group) {
    final List<SBuildType> res = new ArrayList<SBuildType>();
    for (SwabraSettingsGroup g : group) {
      for (SBuildType bt : g.getBuildTypes()) {
        res.add(bt);
      }
    }
    return res;
  }

  @NotNull
  private String signature(@NotNull List<SwabraSettingsGroup> group) {
    StringBuilder sb = new StringBuilder();
    for (SBuildType bt : getBuildTypes(group)) {
      sb.append(bt.getBuildTypeId());
    }
    return sb.toString();
  }
}
