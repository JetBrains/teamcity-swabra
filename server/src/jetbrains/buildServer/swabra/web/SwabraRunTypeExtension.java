package jetbrains.buildServer.swabra.web;

import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.swabra.SwabraUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: vbedrosova
 * Date: 02.10.10
 * Time: 16:19
 */
public class SwabraRunTypeExtension extends RunTypeExtension implements BuildStartContextProcessor {
  private static final String SETTINGS = "swabraSettings.jsp";
  private static final String VIEW_SETTINGS = "viewSwabraSettings.jsp";

  private List<String> mySupportedRunTypes = new ArrayList<String>();

  public final String myEditUrl;
  public final String myViewUrl;

  public SwabraRunTypeExtension(@NotNull final PluginDescriptor descriptor) {
    myEditUrl = descriptor.getPluginResourcesPath(SETTINGS);
    myViewUrl = descriptor.getPluginResourcesPath(VIEW_SETTINGS);
  }

  @Override
  public Collection<String> getRunTypes() {
    return mySupportedRunTypes;
  }

  @Override
  public PropertiesProcessor getRunnerPropertiesProcessor() {
    return null;
  }

  @Override
  public String getEditRunnerParamsJspFilePath() {
    return myEditUrl;
  }

  @Override
  public String getViewRunnerParamsJspFilePath() {
    return myViewUrl;
  }

  @Override
  public Map<String, String> getDefaultRunnerProperties() {
    return Collections.emptyMap();
  }

  //used in spring
  public void setSupportedRunTypes(List<String> supportedRunTypes) {
    mySupportedRunTypes = supportedRunTypes;
  }

  public void updateParameters(@NotNull BuildStartContext context) {
    final Collection<? extends SRunnerContext> runners = context.getRunnerContexts();
    if (!runners.isEmpty()) {
      final Map<String, String> swabraParams = SwabraUtil.getSwabraParameters(runners.iterator().next().getRunParameters());

      for (final Map.Entry<String, String> param : swabraParams.entrySet()) {
        if (param.getValue() != null) {
          context.addSharedParameter(param.getKey(), param.getValue());
        }
      }
    }
  }
}
