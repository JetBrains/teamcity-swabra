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

package jetbrains.buildServer.swabra.processes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: vbedrosova
 * Date: 13.11.10
 * Time: 16:21
 */
public class ProcessInfo {
  @NotNull private final Long myPid;
  @Nullable private final String myName;

  public ProcessInfo(Long pid, String name) {
    myPid = pid;
    myName = name;
  }

  @NotNull
  public Long getPid() {
    return myPid;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ProcessInfo that = (ProcessInfo)o;

    if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;
    if (!myPid.equals(that.myPid)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPid.hashCode();
    result = 31 * result + (myName != null ? myName.hashCode() : 0);
    return result;
  }
}
