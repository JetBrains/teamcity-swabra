/*
 * Copyright 2000-2011 JetBrains s.r.o.
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


import com.intellij.util.io.ZipUtil;
import java.io.*;
import java.net.URL;
import java.util.zip.ZipOutputStream;
import jetbrains.buildServer.serverSide.AgentDistributionMonitor;
import jetbrains.buildServer.serverSide.RegisterAgentPluginException;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 26.02.2010
 * Time: 15:48:35
 */
public class HandleProvider {
  private static final Logger LOG = Logger.getLogger(HandleProvider.class);
  private static final String PLUGIN_DIR_NAME = "handle-provider";

  private static final String HANDLE_PROVIDER_JAR = "handle-provider.jar";
  private static final String TEAMCITY_PLUGIN_XML = "teamcity-plugin.xml";

  private final File myPluginFolder;
  private final AgentDistributionMonitor myAgentManager;

  public HandleProvider(@NotNull final AgentDistributionMonitor agentManager,
                        @NotNull final ServerPaths paths) {
    myAgentManager = agentManager;
    myPluginFolder = new File(paths.getPluginsDir(), PLUGIN_DIR_NAME);
  }

  public boolean isHandlePresent() {
    //TODO: Check windows running build agent to report handle.exe.path config paramter

    if (myPluginFolder == null) {
      return false;
    }
    final File[] files = myPluginFolder.listFiles();
    return files != null && files.length > 0;
  }

  @NotNull
  public File getPluginFolder() {
    return myPluginFolder;
  }

  public void packPlugin(File handleExe) throws IOException {

    final File pluginTempFolder = preparePluginFolder();
    try {
      final File pluginAgentTempFolder = prepareSubFolder(pluginTempFolder, PLUGIN_DIR_NAME);
      try {
        final File binFolder = prepareSubFolder(pluginAgentTempFolder, "bin");
        FileUtil.copy(handleExe, new File(binFolder, "handle.exe"));

        final File libFolder = prepareSubFolder(pluginAgentTempFolder, "lib");
        copyOutResource(libFolder, HANDLE_PROVIDER_JAR);

        final File pluginAgentFolder = prepareSubFolder(pluginTempFolder, "agent");
        zipPlugin(pluginAgentTempFolder, new File(pluginAgentFolder, "handle-provider.zip"));
      } finally {
        FileUtil.delete(pluginAgentTempFolder);
      }
      copyOutResource(pluginTempFolder, TEAMCITY_PLUGIN_XML);
      if (myPluginFolder.exists()) {
        FileUtil.delete(myPluginFolder);
      }
      FileUtil.copyDir(pluginTempFolder, myPluginFolder);
    } finally {
      FileUtil.delete(pluginTempFolder);
    }

    registerAgentPlugin(myPluginFolder);
  }

  private void registerAgentPlugin(@NotNull final File handleServerPlugin) {
    final File[] plugins = new File(handleServerPlugin, "agent").listFiles(new FileFilter() {
      public boolean accept(final File pathname) {
        return pathname.isFile() && pathname.getPath().endsWith(".zip");
      }
    });
    if (plugins == null || plugins.length == 0) return;

    for (File plugin : plugins) {
      try {
        myAgentManager.registerAgentPlugin(plugin);
      } catch (RegisterAgentPluginException e) {
        LOG.warn("Failed to register handle-provider agent plugin: " + e.getLocalizedMessage());
        LOG.debug(e.getMessage(), e);
      }
    }
  }

  private static void copyOutResource(File libFolder, String resourceName) throws FileNotFoundException {
    final String resourcePath = "/bin/" + resourceName;
    final File handleProviderJar = new File(libFolder, resourceName);
    LOG.debug("Copying resource " + resourcePath + " out from jar to " + handleProviderJar + "...");

    FileUtil.copyResource(HandleProvider.class, resourcePath, handleProviderJar);
    if (!handleProviderJar.isFile()) {
      LOG.warn(
        "Unable to copy resource " + resourcePath + " out from jar to " + handleProviderJar + ": " + handleProviderJar + " not found");
      throw new FileNotFoundException(handleProviderJar + " not found");
    }

    LOG.debug("Successfully copied " + resourcePath + " out from jar to " + handleProviderJar);
  }

  private static void zipPlugin(File pluginFolder, File pluginZip) throws IOException {
    LOG.debug("Putting handle-provider plugin from " + pluginFolder + " into zip " + pluginZip + "...");

    ZipOutputStream zipOutputStream = null;
    try {
      zipOutputStream = new ZipOutputStream(new FileOutputStream(pluginZip));
      ZipUtil.addDirToZipRecursively(zipOutputStream, pluginZip, pluginFolder, pluginFolder.getName(), null, null);
    } finally {
      if (zipOutputStream != null) {
        zipOutputStream.close();
      }
    }

    LOG.debug("Successfully put handle-provider plugin from " + pluginFolder + " into zip " + pluginZip);
  }

  private static File prepareSubFolder(File baseFolder, String name) {
    final File binFolder = new File(baseFolder, name);
    if (!binFolder.mkdirs()) {
      LOG.debug("Failed to create subfolder " + binFolder + " for base folder " + baseFolder);
    }
    return binFolder;
  }

  private static File preparePluginFolder() {
    final File pluginFolder = new File(FileUtil.getTempDirectory(), "handle-provider");
    LOG.debug("handle-provider plugin temp folder is " + pluginFolder);

    if (pluginFolder.exists()) {
      LOG.debug("handle-provider plugin folder " + pluginFolder + " exists, trying to delete");
      if (FileUtil.delete(pluginFolder)) {
        LOG.debug("Failed to delete handle-provider plugin folder " + pluginFolder);
      }
    }
    return pluginFolder;
  }
}