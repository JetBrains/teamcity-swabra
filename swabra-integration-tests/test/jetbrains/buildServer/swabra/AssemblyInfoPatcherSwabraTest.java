package jetbrains.buildServer.swabra;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Callable;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.dotNet.assemblyInfo.agent.AssemblyInfoPatcherIntegrationTest;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.util.TestFor;
import org.hamcrest.MatcherAssert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static jetbrains.buildServer.swabra.SwabraTestUtilities.setUpSwabra;
import static jetbrains.buildServer.util.ArchiveUtil.unpackZip;
import static org.hamcrest.core.StringContains.containsString;

/**
 * @User Victory.Bedrosova
 * 3/21/14.
 */
@TestFor(issues = "TW-35645")
public final class AssemblyInfoPatcherSwabraTest extends AssemblyInfoPatcherIntegrationTest {
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
        return AssemblyInfoPatcherSwabraTest.this.getAgentRunningBuild().getBuildLogger();
      }
    });
  }

  /**
   * A modified version of {@link #test_vs2008()}.
   */
  @Test
  public void testVs2008Swabra() {
    final File file = this.getTestDataPath("vs2008.zip");
    unpackZip(file, "", this.myCheckoutDir);

    final Map<String, String> files = this.dumpFile("Lunochod11/Properties/AssemblyInfo.cs",
                                                    "Lunochod12/Properties/AssemblyInfo.cs",
                                                    "Lunochod13/Properties/AssemblyInfo.cs",
                                                    "Lunochod14/AssemblyInfo.cpp",
                                                    "Lunochod15/AssemblyInfo.cpp");

    final SRunningBuild build = this.startBuild(this.myBuildType, false);
    this.finishBuild(build);
    this.dumpBuildLogLocally(build);

    final String buildLog = this.getBuildLog(build);

    for (final String buildLogFragment : asList("Lunochod11",
                                                "Lunochod12",
                                                "Lunochod13",
                                                "Lunochod14",
                                                "Lunochod15",
                                                ".cs",
                                                ".cpp")) {
      /*
       * TW-35645: this assertion fails, as Swabra deletes the "modified"
       * AssemblyInfo.cs before AssemblyInfo patcher runs (no files patched,
       * nothing relevant in the build log).
       *
       * AssemblyInfo patcher position constrains have been changed in 336324.
       */
      MatcherAssert.assertThat(buildLog, containsString(buildLogFragment));
    }

    this.assertVersionPatched(files, "6.5.4", "6.5.4", null);

    /*
     * TW-35645: Only succeeds if Swabra is added to the build using Spring
     * (via @SpringContextFixture), rather than programmatically.
     */
    MatcherAssert.assertThat(buildLog, containsString("Detected 0 unchanged, 0 newly created, 0 modified, 0 deleted files and directories"));
  }

  /**
   * A modified version of {@link #test_vs2010()}.
   */
  @Test
  public void testVs2010Swabra() {
    final File file = this.getTestDataPath("vs2010.zip");
    unpackZip(file, "", this.myCheckoutDir);

    final Map<String, String> files = this.dumpFile("Lunochod1/Lunochod1/Properties/AssemblyInfo.cs",
                                                    "Lunochod1/Lunochod2/AssemblyInfo.cpp",
                                                    "Lunochod1/Lunochod3/My Project/AssemblyInfo.vb",
                                                    "Lunochod1/Lunochod5/Properties/AssemblyInfo.cs",
                                                    "Lunochod1/Lunochod6/Properties/AssemblyInfo.cs",
                                                    "Lunochod1/Lunochod6.Tests/Properties/AssemblyInfo.cs");

    final SRunningBuild build = this.startBuild(this.myBuildType, false);
    this.finishBuild(build);
    this.dumpBuildLogLocally(build);

    final String buildLog = this.getBuildLog(build);

    for (final String buildLogFragment : asList("Lunochod1",
                                                "Lunochod2",
                                                "Lunochod3",
                                                "Lunochod5",
                                                "Lunochod6",
                                                "Lunochod6.Tests",
                                                ".cs",
                                                ".vb",
                                                ".cpp")) {
      /*
       * TW-35645: this assertion fails, as Swabra deletes the "modified"
       * AssemblyInfo.cs before AssemblyInfo patcher runs (no files patched,
       * nothing relevant in the build log).
       *
       * AssemblyInfo patcher position constrains have been changed in 336324.
       */
      MatcherAssert.assertThat(buildLog, containsString(buildLogFragment));
    }

    this.assertVersionPatched(files, "6.5.4", "6.5.4", null);

    /*
     * TW-35645: Only succeeds if Swabra is added to the build using Spring
     * (via @SpringContextFixture), rather than programmatically.
     */
    MatcherAssert.assertThat(buildLog, containsString("Detected 0 unchanged, 0 newly created, 0 modified, 0 deleted files and directories"));
  }
}
