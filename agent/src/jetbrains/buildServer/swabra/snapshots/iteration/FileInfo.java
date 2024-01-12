

package jetbrains.buildServer.swabra.snapshots.iteration;

import jetbrains.buildServer.swabra.SwabraUtil;
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
    myPath = SwabraUtil.unifyPath(path);
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

  @Override
  public String toString() {
    return "FileInfo{" +
           "myPath='" + myPath + '\'' +
           ", myIsFile=" + myIsFile +
           ", myLength=" + myLength +
           ", myLastModified=" + myLastModified +
           '}';
  }
}