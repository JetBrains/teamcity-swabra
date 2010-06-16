package jetbrains.buildServer.swabra.snapshots.rules;

import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 11.06.2010
 * Time: 12:13:16
 */
public class PathRule extends AbstractRule {
  public PathRule(@NotNull String path, boolean exclude) {
    super(path, exclude);
  }

  @Override
  public boolean matches(@NotNull String path) {
    return path.startsWith(getPath());
  }
}
