

package jetbrains.buildServer.swabra;


import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import jetbrains.buildServer.tools.*;
import jetbrains.buildServer.tools.utils.URLDownloader;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.swabra.HandleToolType.HANDLE_EXE;
import static jetbrains.buildServer.swabra.HandleToolType.HANDLE_TOOL;

/**
 * User: vbedrosova
 * Date: 26.02.2010
 * Time: 15:48:35
 */
public class HandleProvider extends ServerToolProviderAdapter {
  private static final Logger LOG = org.apache.log4j.Logger.getLogger(HandleProvider.class.getName());

  private final SSLTrustStoreProvider mySSLTrustStoreProvider;

  @NotNull private final HandleToolType myHandleToolType = HandleToolType.getInstance();
  @NotNull private final ToolVersion mySingleToolVersion = HandleToolVersion.getInstance();

  public HandleProvider(@NotNull final SSLTrustStoreProvider sslTrustStoreProvider) {
    mySSLTrustStoreProvider = sslTrustStoreProvider;
  }

  @NotNull
  @Override
  public ToolType getType() {
    return myHandleToolType;
  }

  @NotNull
  @Override
  public Collection<ToolVersion> getAvailableToolVersions() {
    return Collections.singleton(mySingleToolVersion);
  }

  @NotNull
  @Override
  public File fetchToolPackage(@NotNull final ToolVersion toolVersion, @NotNull final File targetDirectory) throws ToolException {
    final File location = new File(targetDirectory, HANDLE_EXE);
    try {
      final String url = HandleToolType.HTTPS_LIVE_SYSINTERNALS_COM_HANDLE_EXE;
      LOG.info("Downloading package from '" + url + "'");
      URLDownloader.download(url, mySSLTrustStoreProvider.getTrustStore(), location);
    } catch (Throwable e) {
      throw new ToolException("Failed to fetch " + HANDLE_TOOL + ": " + e.getMessage(), e);
    }
    LOG.debug("Successfully downloaded Sysinternals handle.exe to " + location);
    return location;
  }

  @Override
  public void unpackToolPackage(@NotNull final File toolPackage, @NotNull final File targetDirectory) throws ToolException {
    try {
      if(toolPackage.isDirectory())
        FileUtil.copyDir(toolPackage, targetDirectory);
      else
        FileUtil.copy(toolPackage, new File(targetDirectory, HANDLE_EXE));
    } catch (IOException e) {
      throw new ToolException("Failed to copy " + HANDLE_TOOL + " to " + targetDirectory, e);
    }
  }

  @NotNull
  @Override
  public GetPackageVersionResult tryGetPackageVersion(@NotNull final File toolPackage) {
    final String toolPackageName = toolPackage.getName();
    return ((toolPackage.isDirectory() && toolPackageName.equalsIgnoreCase(HANDLE_TOOL)) || (toolPackage.isFile() && toolPackageName.equalsIgnoreCase(HANDLE_EXE)))
           ? GetPackageVersionResult.version(mySingleToolVersion) : GetPackageVersionResult.error(toolPackage.getAbsolutePath() + " is not a valid " + HANDLE_TOOL + " package");
  }
}