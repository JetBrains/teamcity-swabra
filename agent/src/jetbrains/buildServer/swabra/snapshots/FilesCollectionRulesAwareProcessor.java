

package jetbrains.buildServer.swabra.snapshots;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import jetbrains.buildServer.swabra.SwabraLogger;
import jetbrains.buildServer.swabra.SwabraSettings;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 13.05.2010
 * Time: 15:52:19
 */
public class FilesCollectionRulesAwareProcessor extends FilesCollectionProcessor {
  private final SwabraRules myRules;

  public FilesCollectionRulesAwareProcessor(@NotNull SwabraLogger logger,
                                            LockedFileResolver resolver,
                                            @NotNull File dir,
                                            SwabraSettings settings,
                                            AtomicBoolean buildInterrupted) {
    super(logger, resolver, dir, settings.isVerbose(), settings.isLockingProcessesKill(), buildInterrupted);

    myRules = settings.getRules();
  }

  @Override
  public boolean willProcess(FileInfo info) throws InterruptedException {
    if (super.willProcess(info)) {
      return myRules.shouldInclude(info.getPath());
    }
    return false;
  }
}