

package jetbrains.buildServer.swabra.snapshots.iteration;

import org.jetbrains.annotations.Nullable;

/**
 * User: vbedrosova
 * Date: 30.01.2010
 * Time: 14:21:51
 */
public interface FilesIterator {
  /**
   * Gets the next FileInfo
   * @return
   * @throws Exception
   */
  @Nullable FileInfo getNext() throws Exception;

  /**
   * skips iterator
   * @param dirInfo
   */
  void skipDirectory(FileInfo dirInfo);

  /**
   * stops iterators and closes all opened resources and handles
   * @throws Exception
   */
  void stopIterator();

  /**
   * Indicates whether iterator represent current state (not previously saved snapshot)
   * @return
   */
  boolean isCurrent();
}