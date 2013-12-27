package jetbrains.buildServer.swabra.serverHealth;

import com.intellij.util.containers.HashMap;
import java.util.*;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.swabra.SwabraBuildFeature;
import jetbrains.buildServer.swabra.SwabraUtil;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.filters.Filter;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;

/**
 * User: Victory.Bedrosova
 * Date: 4/16/13
 * Time: 11:48 AM
 */
public class SwabraClashingConfigurationsDetectorTest extends TestCase {
  @NotNull
  private Mockery myContext;
  @NotNull
  private SBuildAgent myAgent;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myContext = new JUnit4Mockery();

    myAgent = myContext.mock(SBuildAgent.class, "agent_one");
    myContext.checking(new Expectations() {{
      allowing(myAgent).getAgentTypeId(); will(returnValue(1));
    }});
  }

  @Test
  public void test1BT() throws Exception {
    assertClashingConfigurations(new String[0],
                                 createBT("first", "chechoutDir", "vcsSettingsHash", true, true, true));
  }

  @Test
  public void test2BTNoCDDiffHash() throws Exception {
    assertClashingConfigurations(new String[0],
                                 createBT("first", null, "vcsSettingsHash1", true, true, true),
                                 createBT("second", null, "vcsSettingsHash2", true, true, true));
  }

  @Test
  public void test2BTDiffCDDiffHash() throws Exception {
    assertClashingConfigurations(new String[0],
                                 createBT("first", "chechoutDir1", "vcsSettingsHash1", true, true, true),
                                 createBT("second", "chechoutDir2", "vcsSettingsHash2", true, true, true));
  }

  @Test
  public void test2BTDiffCDSameHash() throws Exception {
    assertClashingConfigurations(new String[0],
                                 createBT("first", "chechoutDir1", "vcsSettingsHash", true, true, true),
                                 createBT("second", "chechoutDir2", "vcsSettingsHash", true, true, true));
  }

  @Test
  public void test2BTSameCDSameHashSameSwabra() throws Exception {
    assertClashingConfigurations(new String[0],
                                 createBT("first", "chechoutDir", "vcsSettingsHash", true, true, true),
                                 createBT("second", "chechoutDir", "vcsSettingsHash", true, true, true));
  }

  @Test
  public void test2BTNoCDSameHashSameSwabra() throws Exception {
    assertClashingConfigurations(new String[0],
                                 createBT("first", null, "vcsSettingsHash", true, true, true),
                                 createBT("second", null, "vcsSettingsHash", true, true, true));
  }

  @Test
  public void test2BTNoCDSameHashSameSwabra1() throws Exception {
    assertClashingConfigurations(new String[0],
                                 createBT("first", null, "vcsSettingsHash", false, false, false),
                                 createBT("second", null, "vcsSettingsHash", false, false, false));
  }

  @Test
  public void test2BTNoCDSameHashSameSwabra2() throws Exception {
    assertClashingConfigurations(new String[0],
                                 createBT("first", null, "vcsSettingsHash", true, false, false),
                                 createBT("second", null, "vcsSettingsHash", true, false, false));
  }

  @Test
  public void test2BTNoCDSameHashSameSwabra3() throws Exception {
    assertClashingConfigurations(new String[0],
                                 createBT("first", null, "vcsSettingsHash", true, true, false),
                                 createBT("second", null, "vcsSettingsHash", true, true, false));
  }

  @Test
  public void test2BTSameCDSameHashDiffSwabra() throws Exception {
    assertClashingConfigurations(getNames("first", "second"),
                                 createBT("first", "chechoutDir", "vcsSettingsHash", true, true, true),
                                 createBT("second", "chechoutDir", "vcsSettingsHash", false, false, false));
  }

  @Test
  public void test2BTNoCDSameHashDiffSwabra() throws Exception {
    assertClashingConfigurations(getNames("first", "second"),
                                 createBT("first", null, "vcsSettingsHash", true, true, true),
                                 createBT("second", null, "vcsSettingsHash", false, false, true));
  }

  @Test
  public void test2BTSameCDSameHashDiffSwabra1() throws Exception {
    assertClashingConfigurations(getNames("first", "second"),
                                 createBT("first", "chechoutDir", "vcsSettingsHash", true, true, true),
                                 createBT("second", "chechoutDir", "vcsSettingsHash", true, false, false));
  }

  @Test
  public void test2BTNoCDSameHashDiffSwabra1() throws Exception {
    assertClashingConfigurations(getNames("first", "second"),
                                 createBT("first", null, "vcsSettingsHash", true, true, true),
                                 createBT("second", null, "vcsSettingsHash", true, false, false));
  }

  @Test
  public void test2BTSameCDSameHashDiffSwabra2() throws Exception {
    assertClashingConfigurations(getNames("first", "second"),
                                 createBT("first", "chechoutDir", "vcsSettingsHash", true, true, true),
                                 createBT("second", "chechoutDir", "vcsSettingsHash", true, true, false));
  }

  @Test
  public void test2BTNoCDSameHashDiffSwabra2() throws Exception {
    assertClashingConfigurations(getNames("first", "second"),
                                 createBT("first", null, "vcsSettingsHash", true, true, true),
                                 createBT("second", null, "vcsSettingsHash", true, true, false));
  }

  @Test
  public void testSeveralBT() throws Exception {
    assertClashingConfigurations(getNames("first", "second", "fifth", "sixth"),
                                 createBT("first", null, "vcsSettingsHash1", false, false, false),
                                 createBT("second", null, "vcsSettingsHash2", false, false, false),
                                 createBT("third", null, "vcsSettingsHash3", false, false, false),
                                 createBT("fourth", null, "vcsSettingsHash4", true, true, true),
                                 createBT("fifth", null, "vcsSettingsHash1", true, true, true),
                                 createBT("sixth", null, "vcsSettingsHash2", true, true, true));
  }

  @Test
  public void testSeveralBTGrouping() throws Exception {
    assertEquals(2, getClashingConfigurations(createBT("first", null, "vcsSettingsHash1", false, false, false),
                                              createBT("second", null, "vcsSettingsHash2", false, false, false),
                                              createBT("third", null, "vcsSettingsHash3", false, false, false),
                                              createBT("fourth", null, "vcsSettingsHash4", true, true, true),
                                              createBT("fifth", null, "vcsSettingsHash1", true, true, true),
                                              createBT("sixth", null, "vcsSettingsHash2", true, true, true)).size());
  }

  @Test
  public void testSeveralBTGrouping1() throws Exception {
    final List<SBuildType> buildTypes = asList(createBT("first", null, "vcsSettingsHash1", false, false, false),
                                               createBT("second", null, "vcsSettingsHash2", false, false, false),
                                               createBT("third", null, "vcsSettingsHash3", false, false, false),
                                               createBT("fourth", null, "vcsSettingsHash4", true, true, true),
                                               createBT("fifth", null, "vcsSettingsHash1", true, true, true),
                                               createBT("sixth", null, "vcsSettingsHash2", true, true, true),
                                               createBT("seventh", null, "vcsSettingsHash3", true, false, false));
    final List<List<SwabraSettingsGroup>> clashingConfigurationsGroups =
      createDetector().getClashingConfigurationsGroups(buildTypes, buildTypes);

    assertEquals(2, clashingConfigurationsGroups.size());

    for (List<SwabraSettingsGroup> group : clashingConfigurationsGroups) {
      assertEquals(2, group.size());
      for (SwabraSettingsGroup g : group) {
        assertEquals(1, g.getBuildTypes().size());
      }
    }
  }

  @Test
  public void testSeveralBTGrouping2() throws Exception {
    final SBuildType scopeBuildType = createBT("first", null, "vcsSettingsHash1", false, false, false);
    final List<SBuildType> buildTypes = asList(scopeBuildType,
                                               createBT("second", null, "vcsSettingsHash2", false, false, false),
                                               createBT("third", null, "vcsSettingsHash3", false, false, false),
                                               createBT("fourth", null, "vcsSettingsHash4", true, true, true),
                                               createBT("fifth", null, "vcsSettingsHash1", true, true, true),
                                               createBT("sixth", null, "vcsSettingsHash2", true, true, true));
    assertEquals(1, getClashingConfigurations(buildTypes, Collections.singletonList(scopeBuildType)).size());
  }

  private void assertClashingConfigurations(@NotNull String[] expectedNames, @NotNull SBuildType... buildTypes) {
    final List<SBuildType> clashing = toSingleList(getClashingConfigurations(buildTypes));
    assertEquals(expectedNames.length, clashing.size());
    for (final String name : expectedNames) {
      assertNotNull(CollectionsUtil.findFirst(clashing, new Filter<SBuildType>() {
        public boolean accept(@NotNull final SBuildType data) {
          return name.equals(data.getName());
        }
      }));
    }
  }

  @NotNull
  private List<SBuildType> toSingleList(@NotNull List<List<SBuildType>> clashing) {
    final List<SBuildType> res = new ArrayList<SBuildType>();
    for (List<SBuildType> bts : clashing) {
      for (SBuildType bt : bts) {
        res.add(bt);
      }
    }
    return res;
  }

  @NotNull
  public List<List<SBuildType>> getClashingConfigurations(@NotNull SBuildType... buildTypes) {
    return getClashingConfigurations(asList(buildTypes), asList(buildTypes));
  }

  @NotNull
  public List<List<SBuildType>> getClashingConfigurations(@NotNull List<SBuildType> buildTypes, @NotNull List<SBuildType> scopeBuildTypes) {
    return createDetector().getClashingConfigurations(buildTypes, scopeBuildTypes);
  }

  @NotNull List<SBuildType> asList(@NotNull SBuildType... buildTypes) {
    return Arrays.asList(buildTypes);
  }

  @NotNull
  private SwabraClashingConfigurationsDetector createDetector() {
    return new SwabraClashingConfigurationsDetector();
  }

  @NotNull
  private String[] getNames(@NotNull String... names) {
    return names;
  }

  @NotNull
  private SBuildType createBT(@NotNull final String name,
                              @Nullable final String checkoutDir,
                              @Nullable final String vcsSettingsHash,
                              final boolean swabraFeaturePresent,
                              final boolean swabraCleanupEnabled,
                              final boolean swabraIsStrict) {

    final SBuildType bt = myContext.mock(SBuildType.class, name);
    myContext.checking(new Expectations() {{
      allowing(bt).getCompatibleAgents(); will(returnValue(Collections.singleton(myAgent)));
      allowing(bt).getName(); will(returnValue(name));
      allowing(bt).isEnabled(with(any(String.class))); will(returnValue(true));
      allowing(bt).getCheckoutDirectory(); will(returnValue(checkoutDir));
      allowing(bt).getVcsSettingsHash(); will(returnValue(vcsSettingsHash));
      allowing(bt).getVcsRoots(); will(returnValue(Collections.emptyList()));
      allowing(bt).getBuildFeaturesOfType(with(SwabraBuildFeature.FEATURE_TYPE)); will(returnValue(swabraFeaturePresent ? Collections.singleton(new SBuildFeatureDescriptor() {
        @NotNull
        public String getId() {
          return "someId";
        }

        @NotNull
        public BuildFeature getBuildFeature() {
          return new BuildFeature() {
            @NotNull
            @Override
            public String getType() {
              return SwabraBuildFeature.FEATURE_TYPE;
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

        @NotNull
        public String getType() {
          return SwabraBuildFeature.FEATURE_TYPE;
        }

        @NotNull
        public Map<String, String> getParameters() {
          final Map<String, String> res = new HashMap<String, String>();
          if (swabraCleanupEnabled) {
            res.put(SwabraUtil.ENABLED, "true");
            res.put(SwabraUtil.STRICT, Boolean.toString(swabraIsStrict));
          }
          return res;
        }
      }) : Collections.emptyList()));
    }});
    return bt;
  }
}
