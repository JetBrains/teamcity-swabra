/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package jetbrains.buildServer.swabra.web;

import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.FormUtil;
import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.swabra.web.actions.BaseAction;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: vbedrosova
 * Date: 26.02.2010
 * Time: 12:19:18
 */
public class HandleController extends BaseFormXmlController {
  @NonNls
  private static final String CONTROLLER_PATH = "/admin/handle.html";
  @NonNls
  private static final String MY_JSP = "handle.jsp";

  @NotNull
  private final PluginDescriptor myPluginDescriptor;
  @NotNull
  private final WebControllerManager myWebControllerManager;
  @NotNull
  private final SecurityContext mySecurityContext;
  @NotNull
  final List<BaseAction> myActions;

  public HandleController(@NotNull final PluginDescriptor pluginDescriptor,
                          @NotNull final WebControllerManager webControllerManager,
                          @NotNull final SecurityContext securityContext,
                          @NotNull final List<BaseAction> actions) {
    myPluginDescriptor = pluginDescriptor;
    myWebControllerManager = webControllerManager;
    mySecurityContext = securityContext;
    myActions = actions;
  }

  public void register() {
    myWebControllerManager.registerController(CONTROLLER_PATH, this);
  }

  @Override
  protected ModelAndView doGet(HttpServletRequest request, HttpServletResponse response) {
    final Map<String, Object> model = new HashMap<String, Object>();

    model.put("handleForm", getForm(request));
    model.put("handlePathPrefix", request.getContextPath() + myPluginDescriptor.getPluginResourcesPath());
    model.put("canLoad", hasPermission());

    return new ModelAndView(myPluginDescriptor.getPluginResourcesPath(MY_JSP), model);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response, Element xmlResponse) {
    if (!hasPermission()) {
      return;
    }

    final HandleForm form = getForm(request);
    form.clearMessages();
    FormUtil.bindFromRequest(request, form);

    final BaseAction action = getAction(form);
    final ActionErrors errors = new ActionErrors();

    action.validate(form, errors);

    if (errors.hasNoErrors()) {
      action.apply(form);
    } else {
      writeErrors(xmlResponse, errors);
    }
  }

  private boolean hasPermission() {
    return AuthUtil.hasGlobalPermission(mySecurityContext.getAuthorityHolder(), Permission.AUTHORIZE_AGENT);
  }

  private static HandleForm getForm(HttpServletRequest request) {
    FormUtil.FormCreator<HandleForm> formCreator = new FormUtil.FormCreator<HandleForm>() {
      public HandleForm createForm(final HttpServletRequest request) {
        final HandleForm form = new HandleForm();
        form.getCameFromSupport().setUrlFromRequest(request, "/admin/serverConfig.html?init=1");
        return form;
      }
    };
    return FormUtil.getOrCreateForm(request, HandleForm.class, formCreator);
  }

  private BaseAction getAction(HandleForm form) {
    final String type = form.getLoadType();
    for (final BaseAction action : myActions) {
      if (action.getType().equals(type)) {
        return action;
      }
    }
    throw new IllegalArgumentException("Support only \"UPLOAD\" and \"DOWNLOAD\" load types");
  }
}
