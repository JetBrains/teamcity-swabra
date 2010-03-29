package jetbrains.buildServer.swabra.web;

import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.controllers.FormUtil;
import jetbrains.buildServer.controllers.ValidationUtil;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.swabra.SwabraHandleProvider;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * User: vbedrosova
 * Date: 26.02.2010
 * Time: 12:19:18
 */
public class HandleController extends BaseFormXmlController {
  @NonNls
  private static final String CONTROLLER_PATH = "/handle.html";
  @NonNls
  private static final String MY_JSP = "handle.jsp";

  private final PluginDescriptor myPluginDescriptor;
  private final WebControllerManager myWebControllerManager;

  public HandleController(final SBuildServer server,
                          final PluginDescriptor pluginDescriptor,
                          final WebControllerManager webControllerManager) {
    super(server);
    myPluginDescriptor = pluginDescriptor;
    myWebControllerManager = webControllerManager;
  }

  public void register() {
    myWebControllerManager.registerController(CONTROLLER_PATH, this);
  }

  @Override
  protected ModelAndView doGet(HttpServletRequest request, HttpServletResponse response) {
    final Map<String, Object> model = new HashMap<String, Object>();
    model.put("handleForm", getForm(request));
    model.put("handlePathPrefix", request.getContextPath() + myPluginDescriptor.getPluginResourcesPath());
    return new ModelAndView(myPluginDescriptor.getPluginResourcesPath(MY_JSP), model);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response, Element xmlResponse) {
    final HandleForm form = getForm(request);
    form.clearMessages();
    FormUtil.bindFromRequest(request, form);

    final ActionErrors errors = new ActionErrors();
    validate(form, errors);
    if (errors.hasNoErrors()) {
      form.setRunning(true);
      form.addMessage("Start downloading Handle.zip from " + form.getUrl() + "...", Status.NORMAL);
      try {
        SwabraHandleProvider.downloadAndExtract(form.getUrl(),
          new File(myServer.getServerRootPath() + "/update/plugins/handle-provider.zip"));
        form.addMessage("Successfully downloaded Handle.zip from " + form.getUrl(), Status.NORMAL);
      } catch (Throwable throwable) {
        form.addMessage("Failed to download Handle, please see teamcity-server.log for details", Status.ERROR);
      }
      form.setRunning(false);
    } else {
      writeErrors(xmlResponse, errors);
    }
  }

  static HandleForm getForm(HttpServletRequest request) {
    FormUtil.FormCreator<HandleForm> formCreator = new FormUtil.FormCreator<HandleForm>() {
      public HandleForm createForm(final HttpServletRequest request) {
        final HandleForm form = new HandleForm();
        form.getCameFromSupport().setUrlFromRequest(request, "/admin/serverConfig.html?init=1");
        return form;
      }
    };
    return FormUtil.getOrCreateForm(request, HandleForm.class, formCreator);
  }

  private void validate(HandleForm form, ActionErrors errors) {
    final String url = form.getUrl();
    if (ValidationUtil.isEmptyOrNull(url)) {
      errors.addError("wrongUrl", "Url is empty");
      return;
    }
    if (!url.startsWith("http://")) {
      errors.addError("wrongUrl", "Url must start with http://");
      return;
    }
    if (!url.endsWith("/Handle.zip")) {
      errors.addError("wrongUrl", "Url must end with /Handle.zip");
      return;
    }
  }
}
