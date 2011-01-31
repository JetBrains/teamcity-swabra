package jetbrains.buildServer.swabra;

import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.FilesCollectionProcessor;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * User: vbedrosova
 * Date: 11.06.2010
 * Time: 20:04:52
 */
public class FilesCollectionProcessorForTests extends FilesCollectionProcessor {
  public FilesCollectionProcessorForTests(@NotNull SwabraLogger logger,
                                          LockedFileResolver resolver,
                                          @NotNull File checkoutDir,
                                          boolean verbose, boolean strict) {
    super(logger, resolver, checkoutDir, verbose, strict);
  }

  @Override
  protected boolean resolveDelete(File f) {
    return true;
  }
}
