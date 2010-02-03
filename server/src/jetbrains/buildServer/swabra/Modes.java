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

import jetbrains.buildServer.controllers.StateField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: vbedrosova
 * Date: 21.04.2009
 * Time: 17:45:45
 */
public class Modes {
  @StateField
  public final List<SwabraMode> myModes;

  public Modes() {
    myModes = new ArrayList<SwabraMode>(SwabraUtil.SWABRA_MODES.size());
    for (Map.Entry<String, String> e : SwabraUtil.SWABRA_MODES.entrySet()) {
      myModes.add(new SwabraMode(e.getKey(), e.getValue()));
    }
  }

  public List<SwabraMode> getModes() {
    return myModes;
  }

  public static class SwabraMode {
    @StateField
    private final String myId;
    @StateField
    private final String myDisplayName;

    public SwabraMode(String id, String displayName) {
      myId = id;
      myDisplayName = displayName;
    }

    public String getId() {
      return myId;
    }

    public String getDisplayName() {
      return myDisplayName;
    }
  }
}
