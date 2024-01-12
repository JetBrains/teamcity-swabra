

package jetbrains.buildServer.swabra;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.FilesCollectionProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 11.06.2010
 * Time: 20:04:52
 */
public class FilesCollectionProcessorForTests extends FilesCollectionProcessor {
  public FilesCollectionProcessorForTests(@NotNull SwabraLogger logger,
                                          LockedFileResolver resolver,
                                          @NotNull File dir,
                                          boolean verbose, boolean strict,
                                          @NotNull AtomicBoolean interruptedFlag) {
    super(logger, resolver, dir, verbose, strict, interruptedFlag);
  }

  @Override
  protected boolean resolveDelete(File f) {
    return true;
  }
}