/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

public class SwabraBuildFeature extends BuildFeature {
  public static final String FEATURE_TYPE = "swabra";

  private final String myEditUrl;

  public SwabraBuildFeature(@NotNull final PluginDescriptor descriptor,
                            @NotNull final WebControllerManager web,
                            @NotNull final HandleProvider handleProvider) {
    final String jsp = descriptor.getPluginResourcesPath("swabraSettings.jsp");
    final String html = descriptor.getPluginResourcesPath("swabraSettings.html");

    web.registerController(html, new BaseController() {
      @Override
      protected ModelAndView doHandle(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        final ModelAndView mv = new ModelAndView(jsp);
        mv.getModel().put("handlePresent", handleProvider.isHandlePresent());
        mv.getModel().put("requestUrl", html);
        mv.getModel().put("buildTypeId", getBuildTypeIdParameter(request));
        return mv;
      }
    });

    myEditUrl = html;
  }

  private String getBuildTypeIdParameter(final HttpServletRequest request) {
    return request.getParameter("id");
  }

  @NotNull
  @Override
  public String getType() {
    return FEATURE_TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Build files cleaner (Swabra)";
  }

  @Override
  public String getEditParametersUrl() {
    return myEditUrl;
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
      if (SwabraUtil.isStrict(params)) {
        result.append("Will force clean checkout if cannot restore clean directory state\n");
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
    if (SwabraUtil.isCleanupEnabled(params) || SwabraUtil.isLockingProcessesDetectionEnabled(params)) {
      final List<String> rules = SwabraUtil.splitRules(SwabraUtil.getRules(params));
      if (!rules.isEmpty()) {
        result.append("Paths to monitor are: ").append(SwabraUtil.getRulesStr(rules)).append("\n");
      }
    }
    if (SwabraUtil.isVerbose(params)) {
      result.append("Output is verbose\n");
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
