/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package jetbrains.buildServer.swabra;

import java.io.File;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: vbedrosova
 * Date: 17.04.2009
 * Time: 15:02:17
 */
public class SwabraUtil {
  public static final String ENABLED = "swabra.enabled";
  public static final String STRICT = "swabra.strict";
  public static final String VERBOSE = "swabra.verbose";

  public static final String LOCKING_PROCESS_KILL = "swabra.kill";
  public static final String LOCKING_PROCESS_DETECTION = "swabra.locking.processes";

  public static final String LOCKING_PROCESS = "swabra.processes";

  public static final String RULES = "swabra.rules";

  private static final String[] KEYS = {ENABLED, STRICT, VERBOSE,
                                        LOCKING_PROCESS_KILL, LOCKING_PROCESS_DETECTION, LOCKING_PROCESS,
                                        RULES};

  public static final String TRUE = "true";

  public static final String AFTER_BUILD = "swabra.after.build";
  public static final String BEFORE_BUILD = "swabra.before.build";

  public static final Map<String, String> SWABRA_MODES = new HashMap<String, String>();
  static {
    SWABRA_MODES.put(AFTER_BUILD, "After build");
    SWABRA_MODES.put(BEFORE_BUILD, "Before next build");
  }

  public static final String CLASHING = "swabra.clashing";

  public static boolean isCleanupEnabled(@NotNull final Map<String, String> params) {
    return params.containsKey(ENABLED);
  }

  public static String getCleanupMode(@NotNull final Map<String, String> params) {
    return params.get(ENABLED);
  }

  public static boolean isAfterBuildCleanup(@NotNull final String mode) {
    return AFTER_BUILD.equalsIgnoreCase(mode);
  }

  public static boolean isAfterBuildCleanup(@NotNull final Map<String, String> params) {
    return isAfterBuildCleanup(getCleanupMode(params));
  }

  public static boolean isVerbose(@NotNull final Map<String, String> params) {
    return Boolean.parseBoolean(params.get(VERBOSE)) && isCleanupEnabled(params);
  }

  public static boolean isLockingProcessesKill(@NotNull final Map<String, String> params) {
    return params.containsKey(LOCKING_PROCESS_KILL) || "kill".equals(params.get(LOCKING_PROCESS));
  }

  public static boolean isStrict(@NotNull final Map<String, String> params) {
    return Boolean.parseBoolean(params.get(STRICT)) && isCleanupEnabled(params);
  }

  public static boolean isLockingProcessesDetectionEnabled(@NotNull final Map<String, String> params) {
    return isLockingProcessesReport(params) || isLockingProcessesReport(params);
  }

  public static boolean isLockingProcessesReport(@NotNull final Map<String, String> params) {
    return params.containsKey(LOCKING_PROCESS_DETECTION) || "report".equals(params.get(LOCKING_PROCESS));
  }

  public static String getRules(@NotNull final Map<String, String> params) {
    return (isCleanupEnabled(params) || isLockingProcessesDetectionEnabled(params)) && params.containsKey(RULES) ? params.get(RULES) : "";
  }

  public static String unifyPath(String path) {
    return unifyPath(path, File.separatorChar);
  }

  public static String unifyPath(String path, char withSeparator) {
    return path.replace('\\', withSeparator).replace('/', withSeparator);
  }

  public static String unifyPath(File file) {
    return unifyPath(file.getAbsolutePath());
  }

  public static Map<String, String> getSwabraParameters(@NotNull final Map<String, String> params) {
    final Map<String, String> swabraParams = new HashMap<String, String>();

    for (final String key : KEYS) {
      swabraParams.put(key, params.get(key));
    }

    return swabraParams;
  }

  public static List<String> splitRules(@NotNull String rules) {
    return rules.length() == 0 ? Collections.<String>emptyList() : Arrays.asList(rules.split(" *[,\n\r] *"));
  }

  @NotNull
  public static String getRulesStr(@NotNull List<String> rules) {
    if (rules.isEmpty()) return "";

    final StringBuilder sb = new StringBuilder();

    if (rules.size() <= RULES_TO_APPEND) {
      appendRules(rules, sb, rules.size());
    } else {
      appendRules(rules, sb, RULES_TO_APPEND);

      final int more = rules.size() - RULES_TO_APPEND;
      sb.append(" and ").append(more).append(" more path").append(more > 1 ? "s" : "");
    }

    return sb.toString().replace("\\", File.separator).replace("/", File.separator);
  }

  private static final int RULES_TO_APPEND = 2;

  private static void appendRules(@NotNull List<String> rules, @NotNull StringBuilder sb, int number) {
    for (int i = 0 ; i < number; ++i) {
      if (i != 0) {
        sb.append(", ");
      }
      sb.append(rules.get(i));
    }
  }

  @NotNull
  public static String toString(@NotNull List<String> list) {
    if (list.isEmpty()) return "";
    final StringBuilder result = new StringBuilder();
    for (String s : list) {
      result.append(s).append(";");
    }
    return result.toString();
  }

  @NotNull
  public static List<String> fromString(@Nullable String s) {
    if (s == null || s.length() == 0) return Collections.emptyList();
    return Arrays.asList(s.split(";"));
  }
}
