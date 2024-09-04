package jetbrains.buildServer.swabra;

import java.util.concurrent.Callable;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.dotNet.assemblyInfo.agent.AssemblyInfoPatcherIntegrationTest;
import jetbrains.buildServer.fileContentReplacer.agent.FileContentReplacerTest;
import jetbrains.buildServer.util.TestFor;
import org.testng.annotations.BeforeMethod;

import static jetbrains.buildServer.swabra.SwabraTestUtilities.setUpSwabra;

/**
 * @author Andrey Shcheglov &lt;mailto:andrey.shcheglov@jetbrains.com&gt;
 */
@TestFor(issues = "TW-35645")
public final class FileContentReplacerSwabraTest extends FileContentReplacerTest {
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
        return FileContentReplacerSwabraTest.this.getAgentRunningBuild().getBuildLogger();
      }
    });
  }
}
