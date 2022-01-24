/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import jetbrains.buildServer.tools.ToolType;
import jetbrains.buildServer.tools.ToolVersion;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.swabra.HandleToolType.HANDLE_TOOL;

public class HandleToolVersion implements ToolVersion {

  private final static HandleToolVersion INSTANCE = new HandleToolVersion();

  private HandleToolVersion() {
  }

  public static HandleToolVersion getInstance() {
    return INSTANCE;
  }

  @NotNull
  @Override
  public ToolType getType() {
    return HandleToolType.getInstance();
  }

  @NotNull
  @Override
  public String getVersion() {
    return "latest";
  }

  @NotNull
  @Override
  public String getId() {
    return HANDLE_TOOL;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return HandleToolType.getInstance().getDisplayName() + " latest version";
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ToolVersion that = (ToolVersion)o;

    if (!HandleToolType.getInstance().getType().equals(that.getType().getType())) return false;
    return HANDLE_TOOL.equals(that.getVersion());

  }

  @Override
  public int hashCode() {
    int result = HandleToolType.getInstance().getType().hashCode();
    result = 31 * result + HANDLE_TOOL.hashCode();
    return result;
  }

}
