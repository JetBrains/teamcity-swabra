package jetbrains.buildServer.swabra.snapshots.rules;

import org.jetbrains.annotations.NotNull;
import org.springframework.util.AntPathMatcher;

/**
 * User: vbedrosova
 * Date: 11.06.2010
 * Time: 12:07:04
 */
public class AntMaskRule extends AbstractRule {
  private static final AntPathMatcher MATCHER = new AntPathMatcher();

  public AntMaskRule(@NotNull String path, boolean exclude) {
    super(path, exclude);
  }

  @Override
  public boolean matches(@NotNull String path) {
    return MATCHER.match(getPath(), path);
  }
}
