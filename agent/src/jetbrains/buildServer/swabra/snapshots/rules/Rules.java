/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
