/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.swabra.serverHealth;

import java.util.Map;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.swabra.SwabraBuildFeature;
import jetbrains.buildServer.swabra.SwabraUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Victory.Bedrosova
 * Date: 4/19/13
 * Time: 5:59 PM
 */
public class SwabraSettings {
  private final boolean myFeaturePresent;
  private final boolean myCleanupEnabled;
  private final boolean myStrict;

  public SwabraSettings(@NotNull SBuildType bt) {
    final SBuildFeatureDescriptor feature = getSwabraBuildFeature(bt);
    myFeaturePresent = feature != null;
    if (myFeaturePresent) {
      final Map<String, String> parameters = feature.getParameters();

      myCleanupEnabled = SwabraUtil.isCleanupEnabled(parameters);
      myStrict = SwabraUtil.isStrict(parameters);
    } else {
      myCleanupEnabled = false;
      myStrict = false;
    }
  }

  @Used("jsp")
  public boolean isCleanupEnabled() {
    return myCleanupEnabled;
  }

  @Used("jsp")
  public boolean isStrict() {
    return myStrict;
  }

  @Used("jsp")
  public boolean isFeaturePresent() {
    return myFeaturePresent;
  }

  private boolean isActualCleanupEnabled() {
    return myFeaturePresent && myCleanupEnabled;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final SwabraSettings that = (SwabraSettings)o;

    return isActualCleanupEnabled() == that.isActualCleanupEnabled() && myStrict == that.myStrict;
  }

  @Override
  public int hashCode() {
    int result = (isActualCleanupEnabled() ? 1 : 0);
    result = 31 * result + (myStrict ? 1 : 0);
    return result;
  }

  @Nullable
  private static SBuildFeatureDescriptor getSwabraBuildFeature(@NotNull SBuildType bt) {
    for (SBuildFeatureDescriptor feature : bt.getBuildFeaturesOfType(SwabraBuildFeature.FEATURE_TYPE)) {
      if (bt.isEnabled(feature.getId())) {
       return feature;
      }
    }
    return null;
  }
}