/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import jetbrains.buildServer.runner.ant.AntRunnerConstants;
import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import jetbrains.buildServer.swabra.SwabraUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * User: Victory.Bedrosova
 * Date: 4/16/13
 * Time: 11:48 AM
 */
@Test
public class SwabraClashingConfigurationDetectorTest extends BaseServerTestCase {
  @NotNull
  private final Map<String, List<String>> myClashing = new HashMap<>();

  @BeforeMethod (alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myClashing.clear();
  }

  @Test
  public void testSingleBT() throws Exception {
    assertNotClashing(Collections.singletonList(createBT("first", true, true, true)));
    assertNotClashing(Collections.singletonList(createBT("second", false, false, false)));
  }

  @Test
  public void testSingleBTClashingItself() throws Exception {
    setClashing("first", "first");
    assertNotClashing(Collections.singletonList(createBT("first", true, true, true)));
  }

  @Test
  public void testTwoBTNotClashing() throws Exception {
    assertNotClashing(asList(createBT("first", true, true, true), createBT("second", false, false, false)));

    setClashing("third", "fourth");
    assertNotClashing(asList(createBT("third", false, false, false), createBT("fourth", false, false, false)));
  }

  @Test
  public void testTwoBTClashing() throws Exception {
    setClashing("first", "second");
    assertClashing(asList(createBT("first", true, true, true), createBT("second", false, false, false)), asList("first"), asList("second"));
  }

  @Test
  public void testThreeBTTwoClashing() throws Exception {
    setClashing("first", "second");
    assertClashing(asList(createBT("first", true, true, true), createBT("second", false, false, false), createBT("third", false, false, false)), asList("first"), asList("second"));
  }

  @Test
  public void testThreeBTClashing() throws Exception {
    setClashing("first", "second", "third");
    assertClashing(asList(createBT("first", true, true, true), createBT("second", false, false, false), createBT("third", false, false, false)), asList("first"), asList("second", "third"));
  }

  @Test
  public void testSeveralBTClashing() throws Exception {
    setClashing("first", "second", "third");
    setClashing("fourth", "second", "fifth");
    assertClashing(asList(createBT("first", true, true, true),
                          createBT("second", false, false, false),
                          createBT("third", true, true, false),
                          createBT("fourth", true, true, true),
                          createBT("fifth", true, false, false),
                          createBT("sixth", true, true, true)),
                   asList("first", "fourth"), asList("second", "fifth"), asList("third"));
  }

  @Test
  public void testSeveralBTClashingGroups1() throws Exception {
    setClashing("first", "second");
    setClashing("third", "second", "fourth");
    setClashing("fifth", "sixth");
    setClashing("seventh", "sixth");
    assertClashing(asList(createBT("first", true, true, true),
                          createBT("second", false, false, false),
                          createBT("third", true, true, true),
                          createBT("fourth", true, true, false),
                          createBT("fifth", true, true, true),
                          createBT("sixth", false, false, false),
                          createBT("seventh", true, true, true)),
                   asList("first", "third"), asList("second"), asList("fourth"));
  }

  @Test
  public void testSeveralBTClashingGroups2() throws Exception {
    setClashing("first", "second");
    setClashing("third", "second", "fourth");
    setClashing("fifth", "sixth");
    setClashing("seventh", "sixth");
    assertClashing(asList(createBT("first", true, true, true),
                          createBT("second", false, false, false),
                          createBT("third", true, true, true),
                          createBT("fourth", true, true, false),
                          createBT("fifth", true, true, true),
                          createBT("sixth", false, false, false),
                          createBT("seventh", true, true, true)),
                   asList("fifth", "seventh"), asList("sixth"));
  }

  private void assertNotClashing(@NotNull Collection<SBuildType> buildTypes) {
    assertEmpty(createDetector().getClashingConfigurationsGroups(buildTypes));
  }

  private void assertClashing(@NotNull Collection<SBuildType> buildTypes, List<String>... groups) {
    final List<List<String>> groupsList = Arrays.asList(groups);
    final List<List<SwabraSettingsGroup>> clashingGroups = createDetector().getClashingConfigurationsGroups(buildTypes);
    assertNotNull(printGroups(clashingGroups), CollectionsUtil.findFirst(clashingGroups, new Filter<List<SwabraSettingsGroup>>() {
      @Override
      public boolean accept(@NotNull final List<SwabraSettingsGroup> settingsGroups) {
        if (settingsGroups.size() == groups.length) {
          for (SwabraSettingsGroup group : settingsGroups) {
            if (null == CollectionsUtil.findFirst(groupsList, new Filter<List<String>>() {
              @Override
              public boolean accept(@NotNull final List<String> data) {
                final List<SBuildType> bts = group.getBuildTypes();
                if (data.size() == bts.size()) {
                  for (SBuildType bt : bts) {
                    if (!data.contains(bt.getName())) return false;
                  }
                  return true;
                }
                return false;
              }
            })) return false;
          }
          return true;
        }
        return false;
      }
    }));
  }

  @NotNull
  private SwabraClashingConfigurationsDetector createDetector() {
    return new SwabraClashingConfigurationsDetector(new SwabraCleanCheckoutWatcher() {
      @NotNull
      @Override
      public Collection<String> getRecentCleanCheckoutCauses(@NotNull final SBuildType buildType) {
        final List<String> clashing = myClashing.get(buildType.getName());
        return clashing == null ? Collections.emptyList() : CollectionsUtil.convertCollection(clashing, new Converter<String, String>() {
          @Override
          public String createFrom(@NotNull final String source) {
            return CollectionsUtil.findFirst(myFixture.getProjectManager().getActiveBuildTypes(), new Filter<SBuildType>() {
              @Override
              public boolean accept(@NotNull final SBuildType data) {
                return data.getName().equals(source);
              }
            }).getBuildTypeId();
          }
        });
      }
    }, myFixture.getProjectManager());
  }

  @NotNull
  private SBuildType createBT(@NotNull final String name,
                              final boolean swabraFeaturePresent,
                              final boolean swabraCleanupEnabled,
                              final boolean swabraIsStrict) {
    final BuildTypeImpl bt = myFixture.createBuildType(name, AntRunnerConstants.RUNNER_TYPE);
    if (swabraFeaturePresent) {
      bt.addBuildFeature(new SBuildFeatureDescriptor() {
        @Override
        @NotNull
        public String getId() {
          return "someId";
        }

        @Override
        @NotNull
        public BuildFeature getBuildFeature() {
          return new BuildFeature() {
            @NotNull
            @Override
            public String getType() {
              return SwabraUtil.FEATURE_TYPE;
            }

            @NotNull
            @Override
            public String getDisplayName() {
              return "Some name";
            }

            @Nullable
            @Override
            public String getEditParametersUrl() {
              return "someUrl.jsp";
            }
          };
        }

        @Override
        @NotNull
        public String getType() {
          return SwabraUtil.FEATURE_TYPE;
        }

        @Override
        @NotNull
        public Map<String, String> getParameters() {
          final Map<String, String> res = new HashMap<String, String>();
          if (swabraCleanupEnabled) {
            res.put(SwabraUtil.ENABLED, "true");
            res.put(SwabraUtil.STRICT, Boolean.toString(swabraIsStrict));
          }
          return res;
        }
      });
    }
    return bt;
  }

  private void setClashing(@NotNull String btId, String... btIds) {
    if (myClashing.containsKey(btId)) throw new IllegalArgumentException();
    myClashing.put(btId, Arrays.asList(btIds));
  }

  @NotNull
  private<T> List<T> asList(T... os) {
    return Arrays.asList(os);
  }

  @NotNull
  private String printGroups(@NotNull List<List<SwabraSettingsGroup>> clashingGroups) {
    final StringBuilder sb = new StringBuilder("Unexpected result:\n");
    for (List<SwabraSettingsGroup> clashing : clashingGroups) {
      sb.append("---------\n");
      for (SwabraSettingsGroup g : clashing) {
        final SwabraSettings settings = g.getSettings();
        sb.append(settings);
        sb.append(g.getBuildTypes());
      }
    }
    return sb.toString();
  }
}
