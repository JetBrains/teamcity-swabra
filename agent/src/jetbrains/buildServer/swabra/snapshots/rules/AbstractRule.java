package jetbrains.buildServer.swabra.snapshots.rules;

import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 10.06.2010
 * Time: 19:22:53
 */
public abstract class AbstractRule {
  @NotNull
  private final String myPath;
  private final boolean myExclude;

  public AbstractRule(@NotNull String path, boolean exclude) {
    myPath = path;
    myExclude = exclude;
  }

  @NotNull
  protected String getPath() {
    return myPath;
  }

  public boolean isExclude() {
    return myExclude;
  }

  public abstract boolean matches(@NotNull String path);
}
