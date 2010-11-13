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


import com.intellij.util.io.ZipUtil;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.zip.ZipOutputStream;

/**
 * User: vbedrosova
 * Date: 26.02.2010
 * Time: 15:48:35
 */
public class HandleProvider {
  private static final Logger LOG = Logger.getLogger(HandleProvider.class);

  private static final String HANDLE_PROVIDER_JAR = "handle-provider.jar";

  private static File myPluginFile;

  // is used in Spring!

  public static void initPluginFile(@NotNull String pluginsDir) {
    myPluginFile = new File(pluginsDir + "/update/plugins/handle-provider.zip");
  }

  public static boolean isHandlePresent() {
    return myPluginFile != null && myPluginFile.isFile();
  }

  public void downloadHandleAndPackPlugin(@NotNull String url) throws Throwable {
    final File tmpFile = new File(FileUtil.getTempDirectory(), "handle.exe");

    downloadHandleExe(url, tmpFile);
    packPlugin(tmpFile);

    FileUtil.delete(tmpFile);
  }

  public void packPlugin(File handleExe) throws IOException {
    LOG.info("Packing " + handleExe.getAbsolutePath() + " into handle-provider plugin to " + myPluginFile.getAbsolutePath() + "...");

    final File pluginTempFolder = preparePluginTempFolder();
    try {
      final File binFolder = prepareSubFolder(pluginTempFolder, "bin");
      FileUtil.copy(handleExe, new File(binFolder, "handle.exe"));

      final File libFolder = prepareSubFolder(pluginTempFolder, "lib");
      copyOutResource(libFolder, HANDLE_PROVIDER_JAR);

      if (myPluginFile.isFile()) {
        LOG.debug(myPluginFile.getAbsolutePath() + " exists, try delete");
        if (FileUtil.delete(myPluginFile)) {
          LOG.error("Failed to delete previously packed " + myPluginFile);
        }
      }

      zipPlugin(pluginTempFolder, myPluginFile);
    } finally {
      FileUtil.delete(pluginTempFolder);
    }
  }

  private static void copyOutResource(File libFolder, String resourceName) throws FileNotFoundException {
    final String resourcePath = "/bin/" + resourceName;
    final File handleProviderJar = new File(libFolder, resourceName);
    LOG.debug("Copying resource " + resourcePath + " out from jar to " + handleProviderJar.getAbsolutePath() + "...");

    FileUtil.copyResource(HandleProvider.class, resourcePath, handleProviderJar);
    if (!handleProviderJar.isFile()) {
      throw new FileNotFoundException("Unable to copy resource " + resourcePath + " out from jar to "
        + handleProviderJar.getAbsolutePath());
    }

    LOG.debug("Successfully copied " + resourcePath + " out from jar to " + handleProviderJar.getAbsolutePath());
  }

  private static void zipPlugin(File pluginFolder, File pluginZip) throws IOException {
    LOG.debug("Putting handle-provider plugin from " + pluginFolder + " into zip "
      + pluginZip.getAbsolutePath() + "...");

    ZipOutputStream zipOutputStream = null;
    try {
      zipOutputStream = new ZipOutputStream(new FileOutputStream(pluginZip));
      ZipUtil.addDirToZipRecursively(zipOutputStream, pluginZip, pluginFolder, pluginFolder.getName(), null, null);
    } finally {
      if (zipOutputStream != null) {
        zipOutputStream.close();
      }
    }

    LOG.debug("Successfully put handle-provider plugin from " + pluginFolder + " into zip "
      + pluginZip.getAbsolutePath());
  }

  private static void downloadHandleExe(String url, File dest) throws IOException {
    URLDownloader.download(new URL(url), dest);
  }

  private static File preparePluginTempFolder() {
    final File pluginFolder = new File(FileUtil.getTempDirectory(), "handle-provider");
    LOG.debug("handle-provider plugin temp folder is " + pluginFolder.getAbsolutePath());

    if (pluginFolder.exists()) {
      LOG.debug("handle-provider plugin folder " + pluginFolder.getAbsolutePath() + " exists, try delete");
      if (FileUtil.delete(pluginFolder)) {
        LOG.error("Failed to delete handle-provider plugin folder " + pluginFolder.getAbsolutePath());
      }
    }
    return pluginFolder;
  }

  private static File prepareSubFolder(File baseFolder, String name) {
    final File binFolder = new File(baseFolder, name);
    if (!binFolder.mkdirs()) {
      LOG.error("Failed to create subfolder " + binFolder.getAbsolutePath() + " for base folder " + baseFolder);
    }
    return binFolder;
  }
}
