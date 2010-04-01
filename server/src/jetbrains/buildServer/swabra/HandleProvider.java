package jetbrains.buildServer.swabra;


import com.intellij.util.io.ZipUtil;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URL;
import java.util.zip.ZipOutputStream;

/**
 * User: vbedrosova
 * Date: 26.02.2010
 * Time: 15:48:35
 */
public class HandleProvider {
  private static final Logger LOG = Logger.getLogger(HandleProvider.class);
  public static final String HANDLE_PROVIDER_JAR = "handle-provider.jar";
  public static final String TEAMCITY_PLUGIN_XML = "teamcity-plugin.xml";

  private static File myPluginFolder;

  public static void initPluginFolder(@NotNull String pluginsDir) {
    myPluginFolder = new File(pluginsDir, "/handle-provider");
  }

  public static boolean isHandlePresent() {
    if (myPluginFolder == null) {
      return false;
    }
    final File[] files = myPluginFolder.listFiles();
    return files != null && files.length > 0;
  }

  public void downloadAndExtract(@NotNull String url) throws Throwable {
    LOG.debug("Downloading Handle.zip from " + url + " and extracting it into handle-provider plugin to "
      + myPluginFolder.getAbsolutePath() + "...");

    final File pluginTempFolder = preparePluginFolder();
    try {
      final File pluginAgentTempFolder = prepareSubFolder(pluginTempFolder, "handle-plugin");
      try {
        final File binFolder = prepareSubFolder(pluginAgentTempFolder, "bin");
        final File handleZip = downloadHandleZip(url);
        extractHandleZip(binFolder, handleZip);

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
//      zipPlugin(pluginTempFolder, pluginFolder);
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

  private static void extractHandleZip(File binFolder, File handleZip) throws IOException {
    LOG.debug("Extracting " + handleZip.getAbsolutePath() + " to " + binFolder.getAbsolutePath() + "...");
    ZipUtil.extract(handleZip, binFolder, new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return "handle.exe".equals(name);
      }
    });
    LOG.debug("Successfully extracted " + handleZip.getAbsolutePath() + " to " + binFolder.getAbsolutePath());
  }

  private static File downloadHandleZip(String url) throws IOException {
    final File tmpFile = FileUtil.createTempFile("", ".zip");
    URLDownloader.download(new URL(url), tmpFile);
    return tmpFile;
  }

  private static File prepareSubFolder(File baseFolder, String name) {
    final File binFolder = new File(baseFolder, name);
    if (!binFolder.mkdirs()) {
      LOG.debug("Failed to create subfolder " + binFolder.getAbsolutePath() + " for base folder " + baseFolder);
    }
    return binFolder;
  }

  private static File preparePluginFolder() {
    final File pluginFolder = new File(FileUtil.getTempDirectory(), "handle-provider");
    LOG.debug("handle-provider plugin temp folder is " + pluginFolder.getAbsolutePath());

    if (pluginFolder.exists()) {
      LOG.debug("handle-provider plugin folder " + pluginFolder.getAbsolutePath() + " exists, try delete");
      if (FileUtil.delete(pluginFolder)) {
        LOG.debug("Failed to delete handle-provider plugin folder " + pluginFolder.getAbsolutePath());
      }
    }
    return pluginFolder;
  }
}
