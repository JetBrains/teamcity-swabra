package jetbrains.buildServer.swabra.snapshots;

import jetbrains.buildServer.swabra.SwabraLogger;
import jetbrains.buildServer.swabra.SwabraSettings;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import jetbrains.buildServer.swabra.snapshots.rules.Rules;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * User: vbedrosova
 * Date: 13.05.2010
 * Time: 15:52:19
 */
public class FilesCollectionRulesAwareProcessor extends FilesCollectionProcessor {
  private final String myCheckouDir;
  private final Rules myRules;

  public FilesCollectionRulesAwareProcessor(@NotNull SwabraLogger logger,
                                            LockedFileResolver resolver,
                                            SwabraSettings settings) {
    super(logger, resolver, settings.isVerbose(), settings.isStrict());

    myCheckouDir = settings.getCheckoutDir().getAbsolutePath();
    myRules = new Rules(settings.getRules());
  }

  @Override
  public boolean willProcess(FileInfo info) {
    final String path = FileUtil.getRelativePath(myCheckouDir, info.getPath(), File.separatorChar);

    if (myRules.exclude(path)) {
      myLogger.debug("Excluded " + info.getPath());
      return false;
    }

    myLogger.debug("Included " + info.getPath());
    return true;
  }
}


