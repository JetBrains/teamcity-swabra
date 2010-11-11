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

package jetbrains.buildServer.swabra;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
//
//  public static void enableSwabra(@NotNull final Map<String, String> params, @NotNull String mode) {
//    params.put(MODE, mode);
//  }

  public static boolean isVerbose(@NotNull final Map<String, String> params) {
    return params.containsKey(VERBOSE) && isCleanupEnabled(params);
  }

  public static boolean isLockingProcessesKill(@NotNull final Map<String, String> params) {
    return SystemInfo.isWindows && (params.containsKey(LOCKING_PROCESS_KILL) || "kill".equals(params.get(LOCKING_PROCESS)));
  }

  public static boolean isStrict(@NotNull final Map<String, String> params) {
    return params.containsKey(STRICT) && isCleanupEnabled(params);
  }

  public static boolean isLockingProcessesReport(@NotNull final Map<String, String> params) {
    return SystemInfo.isWindows && (params.containsKey(LOCKING_PROCESS_DETECTION) || "report".equals(params.get(LOCKING_PROCESS)));
  }

  public static String getRules(@NotNull final Map<String, String> params) {
    return isCleanupEnabled(params) && params.containsKey(RULES) ? params.get(RULES) : "";
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
}
