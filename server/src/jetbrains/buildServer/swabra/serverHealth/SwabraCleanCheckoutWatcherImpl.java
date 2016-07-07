/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.swabra.SwabraUtil;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vbedrosova
 */
public class SwabraCleanCheckoutWatcherImpl extends BuildServerAdapter implements SwabraCleanCheckoutWatcher {

  public static final String CLEAN_CHECKOUT_BUILDS_STORAGE = "swabra.clean.checkout.builds.storage";
  public static final String BUILDS_STORAGE_PERIOD_PROPERTY = "teamcity.healthStatus.swabra.clean.checkout.builds.storage.period";
  public static final String CHECKOUT_DIR_PARAM = "system.teamcity.build.checkoutDir";

  public static final long MONTH = 30*24*3600*1000;
  public static final int HISTORY_LOOKUP_DEPTH = 100;

  private static final Object LOCK = new Object();

  @NotNull private final ProjectManager myProjectManager;
  @NotNull private final ExecutorService myExecutor;

  public SwabraCleanCheckoutWatcherImpl(@NotNull EventDispatcher<BuildServerListener> eventDispatcher,
                                        @NotNull ProjectManager projectManager,
                                        @NotNull ExecutorServices executorServices) {
    myProjectManager = projectManager;
    myExecutor = executorServices.getLowPriorityExecutorService();

    eventDispatcher.addListener(this);
  }



  @Override
  public void buildFinished(@NotNull final SRunningBuild build) {
    if (Boolean.parseBoolean(build.getParametersProvider().get(SwabraUtil.CLEAN_CHECKOUT_DETECTED_PARAM))) {

      final SBuildType buildType = build.getBuildType();
      if (buildType == null) return;

      final String checkoutDir = getCheckoutDir(build);
      if (StringUtil.isEmptyOrSpaces(checkoutDir)) return;

      final SwabraSettings settings = new SwabraSettings(buildType);
      if (!settings.isFeaturePresent()) return; // why are we here?

      final SBuildAgent agent = build.getAgent();

      myExecutor.execute(new Runnable() {
        @Override
        public void run() {

          synchronized (LOCK) {
            final List<SFinishedBuild> history = agent.getBuildHistory(null, true);

            String historyBuildTypeId = null;
            for (int i = 0; i < Math.min(history.size(), HISTORY_LOOKUP_DEPTH); i++) {
              final SFinishedBuild historyBuild = history.get(i);
              if (historyBuild.getBuildId() >= build.getBuildId()) continue;
              if (!checkoutDir.equals(getCheckoutDir(historyBuild))) continue;

              final SBuildType historyBuildType = historyBuild.getBuildType();
              if (historyBuildType == null) continue;

              historyBuildTypeId = historyBuildType.getBuildTypeId();
              break;
            }

            if (historyBuildTypeId != null) {
              getDataStorage(buildType).putValue(historyBuildTypeId, String.valueOf(System.currentTimeMillis()));
            }
          }

        }
      });

      cleanOldValues();
    }
  }

  @Nullable
  private static String getCheckoutDir(@NotNull SBuild build) {
    return build.getParametersProvider().get(CHECKOUT_DIR_PARAM);
  }

  @NotNull
  private CustomDataStorage getDataStorage(final SBuildType buildType) {
    return buildType.getCustomDataStorage(CLEAN_CHECKOUT_BUILDS_STORAGE);
  }

  @Override
  public void serverStartup() {
    cleanOldValues();
  }

  private void cleanOldValues() {
    myExecutor.execute(new Runnable() {
      @Override
      public void run() {

        final long now = System.currentTimeMillis();

        for (SBuildType bt : myProjectManager.getActiveBuildTypes()) {

          synchronized (LOCK) {
            final CustomDataStorage storage = getDataStorage(bt);
            final Map<String, String> values = storage.getValues();
            if (values == null) return;

            for (Map.Entry<String, String> e : values.entrySet()) {
              if (isOldOrBad(e.getValue(), now) || myProjectManager.findBuildTypeById(e.getKey()) == null) {
                storage.putValue(e.getKey(), null);
              }
            }
          }

        }
      }
    });
  }

  private static boolean isOldOrBad(@Nullable String timestamp, long now) {
    try {
      return timestamp == null || now - Long.parseLong(timestamp) > TeamCityProperties.getLong(BUILDS_STORAGE_PERIOD_PROPERTY, MONTH);
    } catch (NumberFormatException e) {
      return true;
    }
  }

  // returns ids of the build types which recently caused swabra clean checkout for the provided build type
  // see teamcity.healthStatus.swabra.builds.storage.period property
  @NotNull
  public Collection<String> getRecentCleanCheckoutCauses(@NotNull SBuildType buildType) {
    synchronized (LOCK) {
      final Map<String, String> values = buildType.getCustomDataStorage(CLEAN_CHECKOUT_BUILDS_STORAGE).getValues();
      return values == null ? Collections.emptyList() : values.keySet();
    }
  }
}
