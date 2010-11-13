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
 * Date: 10.06.2010
 * Time: 19:22:53
 */
public abstract class AbstractRule {
  @NotNull
  private final String myPath;
  private final boolean myExclude;

  public AbstractRule(@NotNull String path, boolean exclude) {
    myPath = path;
    myExclude = exclude;
  }

  @NotNull
  protected String getPath() {
    return myPath;
  }

  public boolean isExclude() {
    return myExclude;
  }

  public abstract boolean matches(@NotNull String path);
}
