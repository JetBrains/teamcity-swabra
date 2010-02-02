package jetbrains.buildServer.swabra.snapshots.iteration;

import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
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
