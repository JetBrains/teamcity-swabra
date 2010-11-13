/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SwabraBuildFeature extends BuildFeature implements BuildStartContextProcessor {
  private final String myEditUrl;

  public SwabraBuildFeature(@NotNull final PluginDescriptor descriptor) {
    myEditUrl = descriptor.getPluginResourcesPath("swabraSettings.jsp");
  }

  @NotNull
  @Override
  public String getType() {
    return "swabra";
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Swabra";
  }

  @Override
  public String getEditParametersUrl() {
    return myEditUrl;
  }

  public void updateParameters(@NotNull BuildStartContext context) {
    SBuildType buildType = context.getBuild().getBuildType();
    if (buildType == null) return;
    Collection<SBuildFeatureDescriptor> buildFeatures = buildType.getBuildFeatures();
    for (SBuildFeatureDescriptor bf: buildFeatures) {
      if (bf.getType().equals(getType())) {
        for (final Map.Entry<String, String> param : bf.getParameters().entrySet()) {
          if (param.getValue() != null) {
            context.addSharedParameter(param.getKey(), param.getValue());
          }
        }
      }
    }
  }

  @Override
  public boolean isMultipleFeaturesPerBuildTypeAllowed() {
    return false;
  }

  @NotNull
  @Override
  public String describeParameters(@NotNull final Map<String, String> params) {
    StringBuilder result = new StringBuilder();
    if (SwabraUtil.isCleanupEnabled(params)) {
      if (SwabraUtil.isAfterBuildCleanup(params)) {
        result.append("Build files cleanup after build enabled\n");
      } else {
        result.append("Build files cleanup before build enabled\n");
      }
    } else {
      result.append("Build files cleanup disabled\n");
    }
    if (SwabraUtil.isLockingProcessesReport(params)) {
      result.append("Will report about processes locking checkout directory\n");
    }
    if (SwabraUtil.isLockingProcessesKill(params)) {
      result.append("Will try to kill processes locking checkout directory\n");
    }
    return result.toString();
  }

  @Override
  public Map<String, String> getDefaultParameters() {
    final Map<String, String> defaults = new HashMap<String, String>(1);
    defaults.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    return defaults;
  }
}
