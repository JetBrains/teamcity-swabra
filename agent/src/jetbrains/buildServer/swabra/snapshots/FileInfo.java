package jetbrains.buildServer.swabra.snapshots;

import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 30.01.2010
 * Time: 16:13:58
 */
public class FileInfo {
  @NotNull
  private final String myPath;
  private final long myLength;
  private final long myLastModified;
  private final boolean myIsFile;

  public FileInfo(@NotNull String path, long length, long lastModified, boolean isFile) {
    myPath = path;
    myLength = length;
    myLastModified = lastModified;
    myIsFile = isFile;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  public long getLastModified() {
    return myLastModified;
  }

  public long getLength() {
    return myLength;
  }

  public boolean isFile() {
    return myIsFile;
  }
}