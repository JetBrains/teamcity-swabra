package jetbrains.buildServer.swabra.snapshots;

import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 30.01.2010
 * Time: 17:22:24
 */
public class FilesTraversal {
  public static interface Visitor {
    void visit(FileInfo file) throws Exception;
  }

  public void traverse(@NotNull FilesIterator it, @NotNull Visitor visitor) throws Exception {
    FileInfo file = it.getNext();
    while (file != null) {
      visitor.visit(file);
      file = it.getNext();
    }
  }
}
