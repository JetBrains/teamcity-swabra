/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package jetbrains.buildServer.swabra.processes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 03.04.2010
 * Time: 13:18:51
 */
public class HandleOutputReader {
  private static final Logger LOG = Logger.getLogger(HandleOutputReader.class);

  private static final String NO_RESULT = "No matching handles found.";
  private static final String NO_ADMIN_RIGHTS = "Make sure that you are an administrator.";

  public static interface LineProcessor {
    void processLine(@NotNull String line);
  }

  public static boolean noResult(@NotNull String handleOutput) {
    return handleOutput.length() == 0 || handleOutput.contains(NO_RESULT);
  }

  public static boolean noAdministrativeRights(@NotNull String handleOutput) {
    return handleOutput.contains(NO_ADMIN_RIGHTS);
  }

  public static void read(@NotNull String handleOutput, @NotNull LineProcessor processor) {
    final BufferedReader reader = new BufferedReader(new StringReader(handleOutput));
    try {
      String line = reader.readLine();
      while (line != null) {
        processor.processLine(line);
        line = reader.readLine();
      }
    } catch (IOException e) {
      LOG.error("IOException when reading", e);
    } finally {
      try {
        reader.close();
      } catch (IOException e) {
        LOG.error("IOException when closing reader", e);
      }
    }
  }
}
