package jetbrains.buildServer.swabra.serverHealth;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.healthStatus.*;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemPageExtension;
import org.jetbrains.annotations.NotNull;

/**
 * User: Victory.Bedrosova
 * Date: 4/15/13
 * Time: 5:35 PM
 *
 * Detects frequent clean checkout cause - build types working in the same checkout directory and having different Swabra settings
 */
public class SwabraFrequentCleanCheckoutReport extends HealthStatusReport {
  private static final String SWABRA_FREQUENT_CLEAN_CHECKOUT_TYPE = "swabraFrequentCleanCheckout";
  public static final String SWABRA_CLASHING_BUILD_TYPES = "swabraClashingBuildTypes";

  @NotNull
  private final ItemCategory myCategory;

  public SwabraFrequentCleanCheckoutReport(@NotNull final PluginDescriptor descriptor,
                                           @NotNull final PagePlaces pagePlaces,
                                           @NotNull final WebLinks webLinks) {
    final HealthStatusItemPageExtension pageExtension = new HealthStatusItemPageExtension(SWABRA_FREQUENT_CLEAN_CHECKOUT_TYPE, pagePlaces);

    pageExtension.setIncludeUrl(descriptor.getPluginResourcesPath("swabraClashingBuildTypes.jsp"));
    pageExtension.register();

    myCategory = new ItemCategory(SWABRA_FREQUENT_CLEAN_CHECKOUT_TYPE,
                                  "Same checkout directory but different Swabra settings",
                                  ItemSeverity.INFO,
                                  webLinks.getHelp("Build+Files+Cleaner+(Swabra)"));
  }

  @NotNull
  @Override
  public String getType() {
    return SWABRA_FREQUENT_CLEAN_CHECKOUT_TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Detect Swabra settings which may cause frequent clean checkout";
  }

  @NotNull
  @Override
  public Collection<ItemCategory> getCategories() {
    return Collections.singleton(myCategory);
  }

  @Override
  public void report(@NotNull final HealthStatusScope scope, @NotNull final HealthStatusItemConsumer resultConsumer) {
    if (!scope.isItemWithSeverityAccepted(ItemSeverity.INFO)) return;

    final List<List<SBuildType>> result = new SwabraClashingConfigurationsDetector().getClashingConfigurations(scope.getBuildTypes());

    for (final List<SBuildType> group: result) {
      if(group.isEmpty()) continue;

      final HealthStatusItem item = new HealthStatusItem(signature(group), myCategory, Collections.<String, Object>singletonMap(SWABRA_CLASHING_BUILD_TYPES, group));

      for(SBuildType affectedBuildType: group) {
        resultConsumer.consumeForBuildType(affectedBuildType, item);
      }
    }
  }

  @NotNull
  private String signature(@NotNull List<SBuildType> buildTypes) {
    StringBuilder sb = new StringBuilder();
    for (SBuildType bt: buildTypes) {
      sb.append(bt.getBuildTypeId());
    }
    return sb.toString();
  }
}
