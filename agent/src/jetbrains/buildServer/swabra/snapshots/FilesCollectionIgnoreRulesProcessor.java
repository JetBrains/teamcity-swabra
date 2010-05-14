package jetbrains.buildServer.swabra.snapshots;

import jetbrains.buildServer.swabra.SwabraLogger;
import jetbrains.buildServer.swabra.SwabraSettings;
import jetbrains.buildServer.swabra.SwabraUtil;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * User: vbedrosova
 * Date: 13.05.2010
 * Time: 15:52:19
 */
public class FilesCollectionIgnoreRulesProcessor extends FilesCollectionProcessor {
  private static final char SEPARATOR = '/';

  private final String myCheckouDir;
  private final List<Pattern> myIgnoredPathsPatterns;
  private final List<String> myIgnoredPaths;

  public FilesCollectionIgnoreRulesProcessor(@NotNull SwabraLogger logger,
                                             LockedFileResolver resolver,
                                             SwabraSettings settings) {
    super(logger, resolver, settings.isVerbose(), settings.isStrict());

    myCheckouDir = settings.getCheckoutDir().getAbsolutePath();

    myIgnoredPathsPatterns = new ArrayList<Pattern>();
    myIgnoredPaths = new ArrayList<String>();

    for (final String p : settings.getIgnoredPaths()) {
      if (p.length() > 0) {
        if (isAntMask(p)) {
          myIgnoredPathsPatterns.add(Pattern.compile(FileUtil.convertAntToRegexp(p)));
        } else {
          myIgnoredPaths.add(p);
        }
      }
    }
  }

  @Override
  public boolean willProcess(FileInfo info) {
    final String path = FileUtil.getRelativePath(myCheckouDir, info.getPath(), File.separatorChar);
    if (isIgnored(path)) {
      myLogger.debug("Ignored " + info.getPath());
      return false;
    }
    return true;
  }

  private boolean isIgnored(String path) {
    if (!myIgnoredPaths.isEmpty()) {
      String p = path;
      do {
        if (myIgnoredPaths.contains(p)) {
          return true;
        }
        p = getParent(p, File.separatorChar);
      } while (p != null);
    }

    if (!myIgnoredPathsPatterns.isEmpty()) {
      String p = SwabraUtil.unifyPath(path, SEPARATOR);
      do {
        for (final Pattern pattern : myIgnoredPathsPatterns) {
          if (pattern.matcher(p).matches()) {
            return true;
          }
        }
        p = getParent(p, SEPARATOR);
      } while (p != null);
    }
    return false;
  }

  private static String getParent(String path, char pathSeparator) {
    int lastSeparator = path.lastIndexOf(pathSeparator);
    return lastSeparator == -1 ? null : path.substring(0, lastSeparator);
  }

  private static boolean isAntMask(String s) {
    return (s.contains("*") || s.contains("?"));
  }
}


