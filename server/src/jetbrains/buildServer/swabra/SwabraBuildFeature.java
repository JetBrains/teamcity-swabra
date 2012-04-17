/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import java.util.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.AjaxRequestProcessor;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.apache.log4j.Logger;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

public class SwabraBuildFeature extends BuildFeature implements BuildStartContextProcessor {
  private static final Logger LOG = Logger.getLogger(SwabraBuildFeature.class);
  public static final String BT_PREFIX = "buildType:";
  public static final String TEMPLATE_PREFIX = "template:";

  @NotNull
  private final BuildServerEx myServer;
  private final String myEditUrl;

  public SwabraBuildFeature(@NotNull BuildServerEx server,
                            @NotNull final PluginDescriptor descriptor,
                            @NotNull final WebControllerManager web,
                            @NotNull final HandleProvider handleProvider) {
    myServer = server;

    final String jsp = descriptor.getPluginResourcesPath("swabraSettings.jsp");
    final String html = descriptor.getPluginResourcesPath("swabraSettings.html");

    web.registerController(html, new BaseController() {
      @Override
      protected ModelAndView doHandle(@NotNull final HttpServletRequest request, @NotNull final HttpServletResponse response) throws Exception {
        if (Boolean.parseBoolean(request.getParameter("updateClashing"))) {
          new AjaxRequestProcessor().processRequest(request, response, new AjaxRequestProcessor.RequestHandler() {
            public void handleRequest(@NotNull final HttpServletRequest request,
                                      @NotNull final HttpServletResponse response,
                                      @NotNull final Element xmlResponse) {
              final Element result = new Element("result");
              for (String c : getClashingConfigurations(request)) {
                final Element buildType = new Element("buildType");
                buildType.setContent(new Text(c));
                result.addContent(buildType);
              }
              xmlResponse.addContent((Content)result);
            }
          });
          return null;
        } else {
          final ModelAndView mv = new ModelAndView(jsp);
          mv.getModel().put("handlePresent", handleProvider.isHandlePresent());
          mv.getModel().put("requestUrl", html);
          mv.getModel().put("buildTypeId", getBuildTypeIdParameter(request));
          return mv;
        }
      }
    });

    myEditUrl = html;
  }

  private List<String> getClashingConfigurations(@NotNull final HttpServletRequest request) {
    return getClashingConfigurations(getBuildTypeId(request), isCleanupEnabled(request), isStrict(request));
  }

  private List<String> getClashingConfigurations(@Nullable final String buildTypeId, final boolean isCleanupEnabled, final boolean isStrict) {
    if (buildTypeId == null) return Collections.emptyList();

    final List<String> clashingConfigurations = new ArrayList<String>();

    try {
      myServer.getSecurityContext().runAsSystem(new SecurityContextEx.RunAsAction() {
        public void run() throws Throwable {
          final SBuildType buildType = myServer.getProjectManager().findBuildTypeById(buildTypeId);

          if (buildType == null) return;

          final String vcsSettingsHash = buildType.getVcsSettingsHash();
          final String checkoutDir = buildType.getResolvedSettings().getCheckoutDirectory();

          for (SBuildType bt : myServer.getProjectManager().getAllBuildTypes()) {
            if (buildTypeId.equals(bt.getBuildTypeId())) continue;

            if (checkoutDir != null && checkoutDir.equals(bt.getResolvedSettings().getCheckoutDirectory()) ||
                vcsSettingsHash.equals(bt.getVcsSettingsHash())) {
              boolean swabraPresent = false;
              for (SBuildFeatureDescriptor feature : bt.getBuildFeatures()) {
                if (bt.isEnabled(feature.getId()) && getType().equals(feature.getType())) {
                  swabraPresent = true;
                  if (isCleanupEnabled != SwabraUtil.isCleanupEnabled(feature.getParameters()) ||
                      isStrict != SwabraUtil.isStrict(feature.getParameters())) {
                    clashingConfigurations.add(bt.getFullName());
                  }
                  break;
                }
              }
              if (!swabraPresent && isCleanupEnabled) clashingConfigurations.add(bt.getFullName());
            }
          }
        }
      });

      return clashingConfigurations;
    } catch (Throwable throwable) {
      LOG.warn("Failed to collect configurations clashing in Swabra plugin settings", throwable);
      return Collections.emptyList();
    }
  }

  private boolean isCleanupEnabled(@NotNull HttpServletRequest request) {
    return request.getParameter(SwabraUtil.ENABLED) != null && !"".equals(request.getParameter(SwabraUtil.ENABLED));
  }

  private boolean isStrict(@NotNull HttpServletRequest request) {
    return request.getParameter(SwabraUtil.STRICT) != null && Boolean.parseBoolean(request.getParameter(SwabraUtil.STRICT));
  }

  @Nullable
  private String getBuildTypeId(@NotNull HttpServletRequest request) {
    final String idParameter = getBuildTypeIdParameter(request);

    if (idParameter == null) return null;

    if (idParameter.startsWith(BT_PREFIX)) {
      return idParameter.substring(BT_PREFIX.length());
    } else if (idParameter.startsWith(TEMPLATE_PREFIX)) {
      return idParameter.substring(TEMPLATE_PREFIX.length());
    }
    return null;
  }

  private String getBuildTypeIdParameter(final HttpServletRequest request) {
    return request.getParameter("id");
  }

  @NotNull
  @Override
  public String getType() {
    return "swabra";
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

  public void updateParameters(@NotNull BuildStartContext context) {
    SBuildType buildType = context.getBuild().getBuildType();
    if (buildType == null) return;
    Collection<SBuildFeatureDescriptor> buildFeatures = buildType.getBuildFeatures();
    for (SBuildFeatureDescriptor bf : buildFeatures) {
      if (buildType.isEnabled(bf.getId()) && bf.getType().equals(getType())) {
        for (final Map.Entry<String, String> param : bf.getParameters().entrySet()) {
          if (param.getValue() != null) {
            context.addSharedParameter(param.getKey(), param.getValue());
          }
        }
        context.addSharedParameter(SwabraUtil.CLASHING, SwabraUtil.toString(
          getClashingConfigurations(buildType.getBuildTypeId(), SwabraUtil.isCleanupEnabled(context.getSharedParameters()),
                                    SwabraUtil.isStrict(context.getSharedParameters()))));
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
