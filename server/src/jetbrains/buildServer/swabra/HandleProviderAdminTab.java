package jetbrains.buildServer.swabra;

import jetbrains.buildServer.serverSide.auth.AuthUtil;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.web.openapi.*;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;

/**
 * User: vbedrosova
 * Date: 29.03.2010
 * Time: 13:36:16
 */
public class HandleProviderAdminTab extends SimpleCustomTab {
  private final SecurityContext mySecurityContext;

  public HandleProviderAdminTab(@NotNull final PagePlaces pagePlaces,
                                @NotNull final SecurityContext securityContext,
                                @NotNull PluginDescriptor pluginDescriptor) {
    super(pagePlaces, PlaceId.ADMIN_SERVER_CONFIGURATION_TAB,
      "handle-provider", pluginDescriptor.getPluginResourcesPath("handleTab.jsp"), "Handle Provider");
    mySecurityContext = securityContext;

//    addJsFile("/js/crypt/rsa.js");
//    addJsFile("/js/crypt/jsbn.js");
//    addJsFile("/js/crypt/prng4.js");
//    addJsFile("/js/crypt/rng.js");
//    addJsFile("/js/bs/encrypt.js");
//    addJsFile("/js/bs/adminActions.js");
//    addJsFile("/js/bs/pluginProperties.js");
//    addJsFile("/clouds/cloud.js");
//    addJsFile("/clouds/admin/cloud-admin.js");
    addJsFile("/js/bs/blocks.js");
    addJsFile("/js/bs/blocksWithHeader.js");
    addJsFile("/js/bs/forms.js");
    addJsFile(pluginDescriptor.getPluginResourcesPath("handle.js"));

//    addCssFile("/clouds/cloud.css");
    addCssFile("/css/forms.css");
    addCssFile("/css/project.css");
    addCssFile("/css/admin/adminMain.css");
    addCssFile("/css/admin/projectForm.css");
    addCssFile("/css/admin/vcsRootsTable.css");
    addCssFile("/css/admin/reportTabs.css");

    setPosition(PositionConstraint.last());
    register();
  }

  @Override
  public boolean isAvailable(@NotNull final HttpServletRequest request) {
    return AuthUtil.hasGlobalPermission(mySecurityContext.getAuthorityHolder(), Permission.ADMINISTER_AGENT);
  }
}
