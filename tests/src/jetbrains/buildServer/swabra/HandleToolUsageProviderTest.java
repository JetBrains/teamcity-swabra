/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

package jetbrains.buildServer.swabra;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.serverSide.BuildAgentEx;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import jetbrains.buildServer.tools.InstalledToolVersionEx;
import jetbrains.buildServer.tools.ServerToolManager;
import jetbrains.buildServer.tools.ToolVersion;
import org.hamcrest.Description;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.swabra.SwabraUtil.*;
import static jetbrains.buildServer.util.CollectionsUtil.asMap;

@Test
public class HandleToolUsageProviderTest extends BaseTestCase {

  private HandleToolUsageProvider myHandleToolUsageProvider;
  private final AtomicReference<InstalledToolVersionEx> myToolHolder = new AtomicReference<>();

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    final Mockery m = new Mockery();
    ServerToolManager serverToolManager = m.mock(ServerToolManager.class);
    final InstalledToolVersionEx swabraTool = m.mock(InstalledToolVersionEx.class);
    myToolHolder.set(swabraTool);
    m.checking(new Expectations() {{
      allowing(serverToolManager).findInstalledTool(with(equal(HandleToolVersion.getInstance().getId())));
      will(new Action() {
        @Override
        public void describeTo(final Description description) {
          description.appendText("returns ").appendValue(myToolHolder.get());
        }

        @Override
        public Object invoke(final Invocation invocation) {
          return myToolHolder.get();
        }
      });
    }});
    myHandleToolUsageProvider = new HandleToolUsageProvider(serverToolManager);
    super.setUp();
  }

  public void testSwabraIsNotRequiredWithoutFeature() {
    doTestToolNotRequired(getBuild("win", asMap(), null));
    doTestToolNotRequired(getBuild("win", asMap(), null));
  }

  public void testSwabraNotRequiredWhenToolNotExist() {
    myToolHolder.set(null);
    doTestToolNotRequired(getBuild("win", asMap(), asMap(LOCKING_PROCESS_KILL, "true")));
    doTestToolNotRequired(getBuild("win", asMap(), asMap(LOCKING_PROCESS_DETECTION, "true")));
    doTestToolNotRequired(getBuild("win", asMap(), asMap(LOCKING_PROCESS, "report")));
    doTestToolNotRequired(getBuild("windows", asMap(), asMap(LOCKING_PROCESS, "kill")));
    doTestToolNotRequired(getBuild("WINDOWS", asMap(), asMap(LOCKING_PROCESS_KILL, "true")));

  }

  public void testSwabraIsNotRequiredWithoutEnabledProcessesDetection() {
    doTestToolNotRequired(getBuild("win", asMap(), asMap()));
    doTestToolNotRequired(getBuild("N/A", asMap(), asMap()));
  }

  public void testSwabraToolIsNotRequiredOnNonWinOS() {
    doTestToolNotRequired(getBuild("linux", asMap(), asMap(LOCKING_PROCESS_KILL, "true")));
    doTestToolNotRequired(getBuild("solar", asMap(), asMap(LOCKING_PROCESS_KILL, "true")));
    doTestToolNotRequired(getBuild("mac", asMap(), asMap(LOCKING_PROCESS_KILL, "true")));
    doTestToolNotRequired(getBuild("BSD", asMap(), asMap(LOCKING_PROCESS_KILL, "true")));
  }

  public void testToolRequiredInCaseOfInvalidOSName() {
    doTestToolRequired(getBuild("N/A", asMap(), asMap(LOCKING_PROCESS_KILL, "true")));
    doTestToolRequired(getBuild("<unknown>", asMap(), asMap(LOCKING_PROCESS_DETECTION, "true")));
  }

  public void testSwabraToolIsRequired() {
    doTestToolRequired(getBuild("win", asMap(), asMap(LOCKING_PROCESS_KILL, "true")));
    doTestToolRequired(getBuild("win", asMap(), asMap(LOCKING_PROCESS_DETECTION, "true")));
    doTestToolRequired(getBuild("win", asMap(), asMap(LOCKING_PROCESS, "kill")));
    doTestToolRequired(getBuild("win", asMap(), asMap(LOCKING_PROCESS, "report")));
    doTestToolRequired(getBuild("windows", asMap(), asMap(LOCKING_PROCESS, "kill")));
    doTestToolRequired(getBuild("WINDOWS", asMap(), asMap(LOCKING_PROCESS, "report")));
    doTestToolRequired(getBuild("WINDOWS", asMap(), asMap(LOCKING_PROCESS_KILL, "true")));
  }

  public void testToolRequiredWhenEnabledInAgentProperties() {
    doTestToolRequired(getBuild("win", asMap(LOCKING_PROCESS_DETECTION, "true"), asMap()));
    doTestToolRequired(getBuild("win", asMap(LOCKING_PROCESS_KILL, "true"), asMap()));

    //one can enable swabra even without feature, just by adding params in configuration/agent properties
    doTestToolRequired(getBuild("win", asMap(LOCKING_PROCESS_DETECTION, "true"), null));
    doTestToolRequired(getBuild("win", asMap(LOCKING_PROCESS_KILL, "true"), null));
    doTestToolRequired(getBuild("WINDOWS", asMap(LOCKING_PROCESS, "report"), null));
  }

  private void doTestToolNotRequired(SRunningBuild build) {
    List<ToolVersion> requiredTools = getRequiredTools(build);
    assertEquals(0, requiredTools.size());
  }

  private void doTestToolRequired(SRunningBuild build) {
    List<ToolVersion> requiredTools = getRequiredTools(build);
    assertEquals(1, requiredTools.size());
    ToolVersion tool = requiredTools.iterator().next();
    assertEquals(myToolHolder.get(), tool);
  }

  private List<ToolVersion> getRequiredTools(SRunningBuild build) {
    return myHandleToolUsageProvider.getRequiredTools(build);
  }

  private SRunningBuild getBuild(final String agentOsName,
                                 @NotNull Map<String, String> agentParams,
                                 @Nullable Map<String, String> featureParams) {
    Mockery m = new Mockery();
    SRunningBuild build = m.mock(SRunningBuild.class);
    BuildAgentEx agent = m.mock(BuildAgentEx.class);
    SAgentType agentType = m.mock(SAgentType.class);

    List<SBuildFeatureDescriptor> features = new ArrayList<>();
    if (featureParams != null) {
      SBuildFeatureDescriptor feature = m.mock(SBuildFeatureDescriptor.class);
      m.checking(new Expectations() {{
        allowing(feature).getParameters(); will(returnValue(featureParams));
        allowing(feature).getType(); will(returnValue(SwabraUtil.FEATURE_TYPE));
      }});
      features.add(feature);
    }

    m.checking(new Expectations() {{
      allowing(build).getAgent(); will(returnValue(agent));
      allowing(build).getBuildOwnParameters(); will(returnValue(asMap()));
      allowing(agent).getConfigurationParameters(); will(returnValue(agentParams));
      allowing(agent).getOperatingSystemName(); will(returnValue(agentOsName));
      allowing(agent).getAgentType(); will(returnValue(agentType));
      allowing(agentType).getOperatingSystemName(); will(returnValue(agentOsName));
      allowing(build).getBuildFeaturesOfType(with(any(String.class))); will(returnValue(features));
    }});

    return build;

  }


}
