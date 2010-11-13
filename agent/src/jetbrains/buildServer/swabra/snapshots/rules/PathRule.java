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

import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 11.06.2010
 * Time: 12:13:16
 */
public class PathRule extends AbstractRule {
  public PathRule(@NotNull String path, boolean exclude) {
    super(path, exclude);
  }

  @Override
  public boolean matches(@NotNull String path) {
    return path.startsWith(getPath());
  }
}
