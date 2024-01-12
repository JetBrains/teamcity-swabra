

package jetbrains.buildServer.swabra;

import jetbrains.buildServer.tools.ToolType;
import jetbrains.buildServer.tools.ToolVersion;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.swabra.HandleToolType.HANDLE_TOOL;

public class HandleToolVersion implements ToolVersion {

  private final static HandleToolVersion INSTANCE = new HandleToolVersion();

  private HandleToolVersion() {
  }

  public static HandleToolVersion getInstance() {
    return INSTANCE;
  }

  @NotNull
  @Override
  public ToolType getType() {
    return HandleToolType.getInstance();
  }

  @NotNull
  @Override
  public String getVersion() {
    return "latest";
  }

  @NotNull
  @Override
  public String getId() {
    return HANDLE_TOOL;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return HandleToolType.getInstance().getDisplayName() + " latest version";
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ToolVersion that = (ToolVersion)o;

    if (!HandleToolType.getInstance().getType().equals(that.getType().getType())) return false;
    return HANDLE_TOOL.equals(that.getVersion());

  }

  @Override
  public int hashCode() {
    int result = HandleToolType.getInstance().getType().hashCode();
    result = 31 * result + HANDLE_TOOL.hashCode();
    return result;
  }

}