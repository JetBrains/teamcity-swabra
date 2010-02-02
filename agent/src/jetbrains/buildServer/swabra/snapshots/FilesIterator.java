package jetbrains.buildServer.swabra.snapshots;

import org.jetbrains.annotations.Nullable;

/**
 * User: vbedrosova
 * Date: 30.01.2010
 * Time: 14:21:51
 */
public interface FilesIterator {
  @Nullable
  FileInfo getNext();
}
