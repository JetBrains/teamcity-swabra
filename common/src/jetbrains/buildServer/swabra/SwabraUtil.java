package jetbrains.buildServer.swabra;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Collection;
import java.util.Arrays;

/**
 * User: vbedrosova
 * Date: 17.04.2009
 * Time: 15:02:17
 */
public class SwabraUtil {
  public static final String SWABRA_ENABLED = "swabra.enabled";

  public static final String TRUE = "true";

  public static boolean isSwabraEnabled(@NotNull final Map<String, String> params) {
    return params.containsKey(SWABRA_ENABLED);
  }

  public static void enableSwabra(@NotNull final Map<String, String> params, boolean enable) {
    if (enable) {
      params.put(SWABRA_ENABLED, TRUE);
    } else {
      params.remove(SWABRA_ENABLED);
    }
  }
}
