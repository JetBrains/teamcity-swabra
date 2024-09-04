package jetbrains.buildServer.swabra;

import java.util.concurrent.Callable;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.dotNet.assemblyInfo.agent.AssemblyInfoPatcherIntegrationTest;
import jetbrains.buildServer.fileContentReplacer.agent.FileContentReplacerIntegrationTest;
import jetbrains.buildServer.util.TestFor;
import org.testng.annotations.BeforeMethod;

import static jetbrains.buildServer.swabra.SwabraTestUtilities.setUpSwabra;

/**
 * @author Andrey Shcheglov &lt;mailto:andrey.shcheglov@jetbrains.com&gt;
 */
@TestFor(issues = "TW-35645")
public final class FileContentReplacerSwabraIntegrationTest extends FileContentReplacerIntegrationTest {
  /**
   * {@inheritDoc}
   *
   * @see AssemblyInfoPatcherIntegrationTest#setUp1()
   */
  @Override
  @BeforeMethod
  public void setUp1() throws Throwable {
    super.setUp1();

    /*
     * Unlike @SpringContextFixture, has no effect.
     */
    setUpSwabra(this, this.myBuildType, new Callable<BuildProgressLogger>() {
      @Override
      public BuildProgressLogger call() throws Exception {
        return FileContentReplacerSwabraIntegrationTest.this.getAgentRunningBuild().getBuildLogger();
      }
    });
  }

  /**
   * {@inheritDoc}
   *
   * @see FileContentReplacerIntegrationTest#getBuildFeatureCount()
   */
  @Override
  protected int getBuildFeatureCount() {
    /*
     * File Content Replacer + Swabra
     */
    return 2;
  }
}
