package jetbrains.buildServer.swabra.snapshots.rules;

import jetbrains.buildServer.swabra.SwabraUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * User: vbedrosova
 * Date: 10.06.2010
 * Time: 19:34:54
 */
public class Rules {
  private static final char PATH_SEPARATOR = '/';

  private static final String EXCLUDE_PREFIX = "-:";
  private static final String INCLUDE_PREFIX = "+:";

  private List<AbstractRule> myRules = new LinkedList<AbstractRule>();

  private static String getPath(String rule) {
    return SwabraUtil.unifyPath(rule.substring(2).trim(), '/');
  }

  private static boolean isAntMask(String path) {
    return (path.contains("*") || path.contains("?"));
  }

  private static AbstractRule createRule(String path, boolean exclude) {
    if (isAntMask(path)) {
      return new AntMaskRule(path, exclude);
    } else {
      return new PathRule(path, exclude);
    }
  }

  public Rules(@NotNull Collection<String> rules) {
    for (String rule : rules) {
      boolean exclude;
      boolean prefixed;

      if (rule.startsWith(EXCLUDE_PREFIX)) {
        exclude = true;
        prefixed = true;
      } else {
        exclude = false;
        prefixed = rule.startsWith(INCLUDE_PREFIX);
      }

      if (prefixed) {
        rule = getPath(rule);
      }

      myRules.add(createRule(rule, exclude));
    }
  }

  public boolean exclude(@NotNull String path) {
    path = SwabraUtil.unifyPath(path, PATH_SEPARATOR);

    boolean exclude = false;
    for (final AbstractRule rule : myRules) {
      if (rule.matches(path)) {
        exclude = rule.isExclude();
      }
    }
    return exclude;
  }
}
