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
public class SwabraHandleProvider {
  private static final Logger LOG = Logger.getLogger(SwabraHandleProvider.class);
  public static final String HANDLE_PROVIDER_JAR = "handle-provider.jar";

  public static void downloadAndExtract(@NotNull String url, @NotNull File pluginZip) throws Throwable {
    LOG.debug(new StringBuilder("Downloading Handle.zip from ").append(url).
      append(" and extracting it into handle-provider plugin to ").append(pluginZip.getAbsolutePath()).append("...").toString());

    final File pluginFolder = preparePluginFolder();
    try {
      final File binFolder = prepareSubFolder(pluginFolder, "bin");
      final File handleZip = downloadHandleZip(url);
      extractHandleZip(binFolder, handleZip);

      final File libFolder = prepareSubFolder(pluginFolder, "lib");
      copyOutLibs(libFolder);
      if (pluginZip.exists()) {
        FileUtil.delete(pluginZip);
      }
      zipPlugin(pluginFolder, pluginZip);
    } finally {
      FileUtil.delete(pluginFolder);
    }
  }

  private static void copyOutLibs(File libFolder) throws FileNotFoundException {
    final String resourceName = "/bin/" + HANDLE_PROVIDER_JAR;
    final File handleProviderJar = new File(libFolder, HANDLE_PROVIDER_JAR);
    LOG.debug("Copying resource " + resourceName + " out from jar to " + handleProviderJar.getAbsolutePath() + "...");
    FileUtil.copyResource(SwabraHandleProvider.class, resourceName, handleProviderJar);
    if (!handleProviderJar.isFile()) {
      throw new FileNotFoundException("Unable to copy resource " + resourceName + " out from jar to "
        + handleProviderJar.getAbsolutePath());
    }
    LOG.debug("Successfully copied " + resourceName + " out from jar to " + handleProviderJar.getAbsolutePath());
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
      LOG.debug("Failed to create subfolder " + binFolder.getAbsolutePath());
    }
    return binFolder;
  }

  private static File preparePluginFolder() {
    final File pluginFolder = new File(FileUtil.getTempDirectory(), "handle-provider");
    LOG.debug("handle-provider plugin folder is " + pluginFolder.getAbsolutePath());
    if (pluginFolder.exists()) {
      LOG.debug("handle-provider plugin folder " + pluginFolder.getAbsolutePath() + " exists, try delete");
      if (FileUtil.delete(pluginFolder)) {
        LOG.debug("Failed to delete handle-provider plugin folder " + pluginFolder.getAbsolutePath());
      }
    }
    return pluginFolder;
  }
}
