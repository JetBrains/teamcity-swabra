/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.WaitFor;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.impl.directories.*;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.swabra.processes.GetProcessesException;
import jetbrains.buildServer.swabra.processes.LockedFileResolver;
import jetbrains.buildServer.swabra.processes.ProcessInfo;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import jetbrains.buildServer.util.Action;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.SystemTimeService;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.swabra.TestUtil.getTestData;
import static jetbrains.buildServer.swabra.TestUtil.getTestDataPath;


/**
 * User: vbedrosova
 * Date: 21.04.2009
 * Time: 15:03:19
 */
@Test
public class SwabraTest extends BaseTestCase {
  private static final String BEFORE_BUILD = "beforeBuild";
  private static final String AFTER_CHECKOUT = "afterCheckout";
  private static final String AFTER_BUILD = "afterBuild";

  private File myCheckoutDir;
  private File myAgentWorkDir;
  private TempFiles myTempFiles;
  private BuildAgentConfigurationEx myAgentConf;
  private BuildAgent myAgent;
  private Mockery myContext;
  private StringBuilder myResults;
  private EventDispatcher<AgentLifeCycleListener> myDispatcher;

  private final AtomicInteger myBuildIdSequence = new AtomicInteger(100);

  private AgentRunningBuild createBuild(@NotNull final Map<String, String> runParams,
                                        @NotNull final File checkoutDir,
                                        @NotNull final SimpleBuildLogger logger) {
    final AgentRunningBuild build = myContext.mock(AgentRunningBuild.class, "build" + myBuildIdSequence.getAndIncrement());

    myContext.checking(new Expectations() {
      {
        allowing(build).getBuildFeaturesOfType(with(SwabraUtil.FEATURE_TYPE));
        will(returnValue(createBuildFeatures(runParams)));
        allowing(build).getBuildLogger();
        will(returnValue(logger));
        allowing(build).getCheckoutDirectory();
        will(returnValue(checkoutDir));
        allowing(build).isCleanBuild();
        will(returnValue(false));
        allowing(build).getSharedConfigParameters();
        will(returnValue(Collections.emptyMap()));
        allowing(build).getEffectiveCheckoutMode();
        will(returnValue(AgentCheckoutMode.ON_SERVER));
        allowing(build).getInterruptReason();
        will(returnValue(null));
        allowing(build).getBuildTypeId();
        will(returnValue("btID"));
        allowing(build).addSharedConfigParameter("swabra.clean.checkout.cause.build.type.id", "btID");


      }
    });

    return build;
  }

  @NotNull
  private Collection<AgentBuildFeature> createBuildFeatures(@NotNull final Map<String, String> runParams) {
    return Collections.<AgentBuildFeature>singletonList(new AgentBuildFeature() {
      @NotNull
      public String getType() {
        return SwabraUtil.FEATURE_TYPE;
      }

      @NotNull
      public Map<String, String> getParameters() {
        return runParams;
      }
    });
  }

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    myContext = new JUnit4Mockery();
    myTempFiles = new TempFiles();
    myAgentWorkDir = myTempFiles.createTempDir();
    myAgentWorkDir.mkdirs();
    myCheckoutDir = new File(myAgentWorkDir, "checkout_dir");
    myCheckoutDir.mkdirs();
    myAgent = myContext.mock(BuildAgent.class);
    myDispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
    myAgentConf = myContext.mock(BuildAgentConfigurationEx.class);
    myContext.checking(new Expectations() {
      {
        allowing(myAgentConf).getCacheDirectory(with(Swabra.CACHE_KEY));
        will(returnValue(myAgentWorkDir));
        allowing(myAgentConf).getWorkDirectory();
        will(returnValue(myAgentWorkDir));
        allowing(myAgent).getConfiguration();
        will(returnValue(myAgentConf));
      }
    });
  }

  @Override
  @AfterMethod
  public void tearDown() throws Exception {
    myTempFiles.cleanup();
    super.tearDown();
  }

  private void runTest(final String dirName, final String resultsFileName,
                       Map<String, String>... params) throws Exception {
    runTest(dirName, resultsFileName, null, null, params);
  }
  private void runTest(final String dirName, final String resultsFileName,
                       Runnable afterBuildActions, List<File> extraDirs,
                       Map<String, String>... params) throws Exception {
    final String goldFile = getTestDataPath(resultsFileName + ".gold", null);
    final String resultsFile = goldFile.replace(".gold", ".tmp");
    System.setProperty(Swabra.TEST_LOG, resultsFile);

    new File(resultsFile).delete();

    myResults = new StringBuilder();

    final SimpleBuildLogger logger = new BuildProgressLoggerMock(myResults);
    final SwabraLogger swabraLogger = new SwabraLogger();
    final DirectoryCleaner directoryCleaner = new DirectoryCleaner() {
      public boolean delete(@NotNull final File f, @NotNull final DirectoryCleanerCallback callback) {
        return false;
      }

      public boolean deleteNow(@NotNull final File f, @NotNull final DirectoryCleanerCallback callback) {
        return false;
      }
    };

    final Swabra swabra = new Swabra(myDispatcher, new SwabraLogger(),
                                     new SwabraPropertiesProcessor(myDispatcher, swabraLogger,
                                                                   new DirectoryMapPersistanceImpl(myAgentConf, new SystemTimeService())),
                                     new BundledToolsRegistry() {
                                       @Nullable
                                       public BundledTool findTool(@NotNull final String name) {
                                         return null;
                                       }

                                       @Override
                                       public void registerTool(@NotNull final String toolName, @NotNull final BundledTool tool) {

                                       }
                                     },
                                     new DirectoryMapDirectoriesCleanerImpl(myDispatcher,
                                              directoryCleaner,
                                              new DirectoryMapPersistanceImpl(myAgentConf, new SystemTimeService()),
                                              new DirectoryMapDirtyTrackerImpl() )
    );

//    final File pttTemp = new File(TEST_DATA_PATH, "ptt");
//    System.setProperty(ProcessTreeTerminator.TEMP_PATH_SYSTEM_PROPERTY, pttTemp.getAbsolutePath());


    myDispatcher.getMulticaster().afterAgentConfigurationLoaded(myAgent);
    myDispatcher.getMulticaster().agentStarted(myAgent);

    final String checkoutDirPath = myCheckoutDir.getAbsolutePath();

    final Map<String, String> runParams = new HashMap<String, String>();
    final AgentRunningBuild build = createBuild(runParams, myCheckoutDir, logger);
    final BuildRunnerContext runner = myContext.mock(BuildRunnerContext.class, "context"+System.currentTimeMillis());

    for (Map<String, String> param : params) {
      runParams.clear();
      runParams.putAll(param);
      runBuild(dirName, myDispatcher, build, runner, checkoutDirPath);
      if (afterBuildActions != null) {
        afterBuildActions.run();
      }
    }

    String text = FileUtil.readText(new File(resultsFile));
    final String baseText = text.trim();
    String actual = baseText.replace(myCheckoutDir.getAbsolutePath(), "##CHECKOUT_DIR##");
    if (extraDirs!= null) {
      for (int i = 0; i < extraDirs.size(); i++) {
        actual = baseText.replace(extraDirs.get(i).getAbsolutePath(), "##EXTRA_DIR_" + (i + 1) + "##");
      }
    }
    actual = actual.replace("/", "\\");
    final String expected = FileUtil.readText(new File(goldFile)).trim();
    assertEquals(actual, expected, actual);
//    FileUtil.delete(pttTemp);
  }

  private void runBuild(String dirName, EventDispatcher<AgentLifeCycleListener> dispatcher, AgentRunningBuild build,
                        BuildRunnerContext runner, String checkoutDirPath) throws Exception {
    FileUtil.copyDir(getTestData(dirName + File.separator + BEFORE_BUILD, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().buildStarted(build);
    Thread.sleep(100);
    cleanCheckoutDir();

    FileUtil.copyDir(getTestData(dirName + File.separator + AFTER_CHECKOUT, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().sourcesUpdated(build);
    Thread.sleep(100);
    dispatcher.getMulticaster().beforeRunnerStart(runner);
    Thread.sleep(100);
    dispatcher.getMulticaster().beforeBuildFinish(build, BuildFinishedStatus.FINISHED_SUCCESS);
    Thread.sleep(100);
    cleanCheckoutDir();

    FileUtil.copyDir(getTestData(dirName + File.separator + AFTER_BUILD, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().afterAtrifactsPublished(build, BuildFinishedStatus.FINISHED_SUCCESS);
    Thread.sleep(100);

    myContext.assertIsSatisfied();
  }

  private void cleanCheckoutDir() {
    final File[] files = myCheckoutDir.listFiles();
    if (files != null && files.length != 0) {
      for (File file : files) {
        FileUtil.delete(file);
      }
    }
  }

  public void testEmptyCheckoutDirNonStrict() throws Exception {
    prepareEmptyCheckoutDir();
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("emptyCheckoutDir", "emptyCheckoutDir_b", firstCallParams, secondCallParams);
  }

  public void testEmptyCheckoutDirStrict() throws Exception {
    prepareEmptyCheckoutDir();
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    firstCallParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    secondCallParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("emptyCheckoutDir", "emptyCheckoutDir_b", firstCallParams, secondCallParams);
  }

  public void testEmptyCheckoutDirAfterBuild() throws Exception {
    prepareEmptyCheckoutDir();
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("emptyCheckoutDir", "emptyCheckoutDir_a", firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedNonStrict() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_b",
      firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedStrict() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    firstCallParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    secondCallParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_b",
      firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedAfterBuild() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_a",
      firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedBeforeAfterNonStrict() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_b_a",
      firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedBeforeAfterStrict() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_b_a",
      firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedAfterBeforeNonStrict() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_a_b",
      firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedAfterBeforeStrict() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_a_b",
      firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedTurnedOffAfter() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_off_a",
      firstCallParams, secondCallParams);
  }

  public void testOneDeletedAfterBuild() throws Exception {
    prepareOneDeletedDir();
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("oneDeleted", "oneDeleted_a",
      firstCallParams, secondCallParams);
  }

  public void testOneDeletedNonStrict() throws Exception {
    prepareOneDeletedDir();
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("oneDeleted", "oneDeleted_a",
      firstCallParams, secondCallParams);
  }

  public void testOneDeletedStrict() throws Exception {
    prepareOneDeletedDir();
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("oneDeleted", "oneDeleted_a",
      firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedNonStrict3() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> thirdCallParams = new HashMap<String, String>();
    thirdCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    thirdCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_b_3",
      firstCallParams, secondCallParams, thirdCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedStrict3() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> thirdCallParams = new HashMap<String, String>();
    thirdCallParams.put(SwabraUtil.ENABLED, SwabraUtil.TRUE);
    thirdCallParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);
    thirdCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_b_3",
      firstCallParams, secondCallParams, thirdCallParams);
  }

  public void testBuildInterruptedDuringCleanup() throws Exception {
    try {
      System.setProperty(Swabra.TEST_LOG, "true");
      final File[] files = myCheckoutDir.listFiles();
      for (File file : files) {
        file.delete(); //delete useless files
      }

      new File(myCheckoutDir, "test1").createNewFile();
      new File(myCheckoutDir, "test2").createNewFile();
      new File(myCheckoutDir, "test3").createNewFile();
      new File(myCheckoutDir, "test4").createNewFile();
      new File(myCheckoutDir, "test5").createNewFile();

      final StringBuilder results = new StringBuilder();

      final SimpleBuildLogger logger = new BuildProgressLoggerMock(results);
      final SwabraLogger swabraLogger = new SwabraLogger();
      final SwabraPropertiesProcessor propertiesProcessor =
        new SwabraPropertiesProcessor(myDispatcher, swabraLogger, new DirectoryMapPersistanceImpl(myAgentConf, new SystemTimeService())) {
        @Override
        public DirectoryState getState(final File dir) {
          return dir.equals(myCheckoutDir) ? DirectoryState.PENDING : DirectoryState.UNKNOWN;
        }

        @Override
        public File getSnapshotFile(final File dir) {
          FileWriter writer  = null;
          File snapshotFile = null;
          try {
            snapshotFile = createTempFile();
            writer = new FileWriter(snapshotFile);
            writer.write(dir.getAbsolutePath());
          } catch (IOException ignored) {
          } finally {
            if (writer != null){
              try {writer.close();} catch (IOException ignored) {}
            }
          }
          return snapshotFile;
        }
      };

      final DirectoryCleaner directoryCleaner = new DirectoryCleaner() {
        public boolean delete(@NotNull final File f, @NotNull final DirectoryCleanerCallback callback) {
          return false;
        }

        public boolean deleteNow(@NotNull final File f, @NotNull final DirectoryCleanerCallback callback) {
          return false;
        }
      };
      final Swabra swabra = new Swabra(myDispatcher, new SwabraLogger(),
                                       propertiesProcessor, new BundledToolsRegistry() {
        public BundledTool findTool(@NotNull final String name) {
          return null;
        }

        @Override
        public void registerTool(@NotNull final String toolName, @NotNull final BundledTool tool) {

        }
      }, new DirectoryMapDirectoriesCleanerImpl(myDispatcher,
                                                directoryCleaner,
                                                new DirectoryMapPersistanceImpl(myAgentConf, new SystemTimeService()),
                                                new DirectoryMapDirtyTrackerImpl()
      ));

      final CountDownLatch latch = new CountDownLatch(2);
      final AtomicBoolean interruptedFlag = new AtomicBoolean(false);
      final AtomicInteger numberFilesProcessed = new AtomicInteger(0);

      swabra.setInternalProcessor(new FilesCollectionProcessorForTests(swabraLogger, null, myCheckoutDir, true, true, interruptedFlag) {
        @Override
        public boolean willProcess(final FileInfo info) throws InterruptedException {
          numberFilesProcessed.incrementAndGet();
          System.out.println(info.toString());
          latch.countDown();
          Thread.sleep(2000);
          return super.willProcess(info);
        }
      }, interruptedFlag);

      myDispatcher.getMulticaster().afterAgentConfigurationLoaded(myAgent);
      myDispatcher.getMulticaster().agentStarted(myAgent);

      final Map<String, String> runParams = new HashMap<String, String>();
      final AgentRunningBuild build = createBuild(runParams, myCheckoutDir, logger);

      final Thread interruptThread = new Thread(new Runnable() {
        public void run() {
          try {
            latch.await(100, TimeUnit.SECONDS);
            myDispatcher.getMulticaster().beforeBuildInterrupted(build, BuildInterruptReason.SERVER_STOP_BUILD);
          } catch (InterruptedException ignored) {
          }
        }
      });
      interruptThread.start();

      final Map<String, String> map = new HashMap<String, String>();
      map.put(SwabraUtil.ENABLED, SwabraUtil.TRUE);
      runParams.putAll(map);

      myDispatcher.getMulticaster().buildStarted(build);

      // processed 2 of 5 files
      assertEquals(2, numberFilesProcessed.get());
    } finally {
      System.getProperties().remove(Swabra.TEST_LOG);
    }
  }

  // TW-29332
  public void testUnicodeFileNames_unchanged() throws Exception {
    final long lastModified = System.currentTimeMillis();
    final File testData = new File(getTestData(null, null), "unicodeFileNames_unchanged");
    FileUtil.delete(testData);
    for (String dirName : Arrays.asList("beforeBuild", "afterBuild", "afterCheckout")){
      final File dir = new File(testData, dirName);
      FileUtil.createDir(dir);
      for (String fileName : Arrays.asList(
        "\u0024\u0025\u0027\u0060\u002d\u0040\u007b\u007d\u007e\u0021\u0023\u0028\u0029\u0026\u005f\u005e",
        "+file",
        "-file",
        ".File",
        "347",
        "\u003d\u005d\u005b\u003b\u002e\u002c\u002b\u2116",
        "=file",
        "file name (with brackets)",
        "many.ext.en.sion.s",
        "\u0072\u0065\u0073\u0074\u0061\u0072\u0074\u0069\u006e\u0067\u0040\u0032\u0078\u002e\u0070\u006e\u0067",
        "\u0075\u006e\u0069\u0063\u006f\u0064\u0065\u002d\u00e4\u00df\u00e1\u00e1\u00f3\u00f6\u00e9\u00c4\u00fa\u00fc\u002d\u044b\u0439\u0401\u002e\u043f\u044b\u0445\u002d\u00ff",
        "\u0078\u0073\u0073\u005f\u0074\u0072\u0079\u005f\u0025\u0032\u0037\u0025\u0032\u0032\u0025\u0033\u0043\u0073\u0063\u0072\u0069\u0070\u0074\u0025\u0033\u0045\u0061\u006c\u0065\u0072\u0074\u0028\u0036\u0036\u0036\u0029\u0025\u0033\u0042\u0025\u0033\u0043\u0025\u0032\u0046\u0073\u0063\u0072\u0069\u0070\u0074\u0025\u0033\u0045\u002e\u0070\u006e\u0067",
        "\u0444\u0430\u0439\u043b\u043e"
      )){
        final File file = new File(dir, fileName);
        FileUtil.writeFileAndReportErrors(file, "some text");
        file.setLastModified(lastModified);
      }
      dir.setLastModified(lastModified);
    }

    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> thirdCallParams = new HashMap<String, String>();
    thirdCallParams.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    thirdCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("unicodeFileNames_unchanged", "unicodeFileNames_unchanged", firstCallParams, secondCallParams, thirdCallParams);
  }

  // TW-31015
  // Actually inpassing null CollectionResultHandler to jetbrains.buildServer.swabra.snapshots.FilesCollector.collect()
  public void testCleanNonCheckoutDir() throws Exception {
    prepareEmptyCheckoutDir();
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final File tmpNonCheckoutDir = myTempFiles.createTempDir();
    final File tempFileInDir = new File(tmpNonCheckoutDir, "ttt.txt");
    firstCallParams.put(SwabraUtil.RULES, tmpNonCheckoutDir.getAbsolutePath());


    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.RULES, tmpNonCheckoutDir.getAbsolutePath());

    final AtomicInteger runCount = new AtomicInteger(0);


    runTest("emptyCheckoutDir", "nonCheckoutDir", new Runnable() {
      public void run() {
        if (runCount.incrementAndGet() == 1) {
          try {
            tempFileInDir.createNewFile();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }, Arrays.asList(tmpNonCheckoutDir), firstCallParams, secondCallParams);

    assertFalse(tempFileInDir.exists());
  }

  //TW-39748
  public void testDontCleanExternalCheckoutDirsOnAgentStart() throws Exception {
    final File checkoutDir1 = new File(myAgentWorkDir, "checkoutDir1");
    checkoutDir1.mkdirs();
    final File checkoutDir2 = new File(myAgentWorkDir, "checkoutDir2/dir2");
    checkoutDir2.mkdirs();
    final File checkoutDir3 = myTempFiles.createTempDir();
    checkoutDir3.mkdirs();

    final List<DirectoryMapItem> items = new ArrayList<DirectoryMapItem>();
    items.add(new DirectoryMapItem("bt1", "build1", checkoutDir1, System.currentTimeMillis(), DirectoryLifeTime.getDefault()));
    items.add(new DirectoryMapItem("bt2", "build2", checkoutDir2, System.currentTimeMillis(), DirectoryLifeTime.getDefault()));
    items.add(new DirectoryMapItem("bt3", "build3", checkoutDir3, System.currentTimeMillis(), DirectoryLifeTime.getDefault()));

    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    final StringBuilder results = new StringBuilder();

    final SimpleBuildLogger logger = new BuildProgressLoggerMock(results);

    doWholeTest(items, new Action<Pair<Swabra, SwabraPropertiesProcessor>>() {
      public void apply(final Pair<Swabra, SwabraPropertiesProcessor> pair) {
        final Swabra swabra = pair.getFirst();
        final SwabraPropertiesProcessor propertiesProcessor = pair.getSecond();
        try {
          for (final DirectoryMapItem item : items) {
            final File folder = item.getLocation();
            final AgentRunningBuild build = createBuild(firstCallParams, folder, logger);
            swabra.buildStarted(build);
            folder.mkdirs();
            File file = new File(folder, "file.txt");
            file.createNewFile();
            swabra.sourcesUpdated(build);
            swabra.afterAtrifactsPublished(build, BuildFinishedStatus.FINISHED_SUCCESS);
            swabra.buildFinished(build, BuildFinishedStatus.FINISHED_SUCCESS);
          }
          propertiesProcessor.agentStarted(myAgent);
          new WaitFor(1000) {
            @Override
            protected boolean condition() {
              return propertiesProcessor.isInitialized();
            }
          };
        } catch (Exception ex) {
          ex.printStackTrace();
          fail(ex.toString());
        }
      }
    });



    /*
E:\TEMP\test-1307328584\checkoutDir1=pending
E:\TEMP\test-253694471=pending
E:\TEMP\test-1307328584\checkoutDir2\dir2=pending
    * */


    final File snapshotMap = new File(myAgentWorkDir, SwabraPropertiesProcessor.FILE_NAME);
    final List<String> strings = FileUtil.readFile(snapshotMap);
    for (String string : strings) {
      boolean okay = false;
      for (DirectoryMapItem item : items) {
        final String location = item.getLocation().getAbsolutePath();
        final String[] split = string.split("=");
        okay = okay || split[0].equals(location);
      }

      assertTrue("No matching entry for " + string, okay);
    }

    final File[] list = myAgentWorkDir.listFiles(new FilenameFilter() {
      public boolean accept(final File dir, final String name) {
        return name.endsWith(".snapshot");
      }
    });
    assertEquals(3, list.length);

  }

  //TW-39812
  public void testDontCleanExtraDirsOnAgentStart() throws Exception {
    final File checkoutDir1 = new File(myAgentWorkDir, "checkoutDir1");
    checkoutDir1.mkdirs();
    final File dir2 = new File(checkoutDir1, "dir2");
    dir2.mkdirs();
    final File externalDir3 = myTempFiles.createTempDir();
    externalDir3.mkdirs();

    final List<DirectoryMapItem> items = new ArrayList<DirectoryMapItem>();
    items.add(new DirectoryMapItem("bt1", "build1", checkoutDir1, System.currentTimeMillis(), DirectoryLifeTime.getDefault()));

    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    firstCallParams.put(SwabraUtil.RULES
      , String.format("-:%s\n+:%s/\n%s"
      , checkoutDir1.getAbsolutePath()
      , dir2.getAbsolutePath()
      , externalDir3.getAbsolutePath()));

    final StringBuilder results = new StringBuilder();

    final SimpleBuildLogger logger = new BuildProgressLoggerMock(results);

    doWholeTest(items, new Action<Pair<Swabra, SwabraPropertiesProcessor>>() {
      public void apply(final Pair<Swabra, SwabraPropertiesProcessor> pair) {
        try {
          final Swabra swabra = pair.getFirst();
          final SwabraPropertiesProcessor propertiesProcessor = pair.getSecond();
          final AgentRunningBuild build = createBuild(firstCallParams, checkoutDir1, logger);
          swabra.buildStarted(build);
          checkoutDir1.mkdirs();
          File file = new File(checkoutDir1, "file.txt");
          dir2.mkdirs();
          file.createNewFile();
          swabra.sourcesUpdated(build);
          swabra.afterAtrifactsPublished(build, BuildFinishedStatus.FINISHED_SUCCESS);
          swabra.buildFinished(build, BuildFinishedStatus.FINISHED_SUCCESS);

          propertiesProcessor.agentStarted(myAgent);
          new WaitFor(1000) {
            @Override
            protected boolean condition() {
              return propertiesProcessor.isInitialized();
            }
          };

        } catch (Exception e) {
          e.printStackTrace();
          fail(e.toString());
        }
      }
    });

    /*
E:\TEMP\test-1307328584\checkoutDir1=pending
E:\TEMP\test-253694471=pending
E:\TEMP\test-1307328584\checkoutDir2\dir2=pending
    * */

    final File snapshotMap = new File(myAgentWorkDir, SwabraPropertiesProcessor.FILE_NAME);
    final List<String> strings = FileUtil.readFile(snapshotMap);
    boolean dir2Present = false;
    boolean externalDirPresent = false;
    for (String string : strings) {
      final String location = checkoutDir1.getAbsolutePath();
      final String[] split = string.split("=");
      dir2Present = dir2Present || string.startsWith(dir2.getAbsolutePath());
      externalDirPresent = externalDirPresent ||string.startsWith(externalDir3.getAbsolutePath());
      assertTrue("No matching entry for " + string, split[2].equals(location));
    }

    assertTrue("Dir2 should be there",dir2Present);
    assertTrue("externalDir should be there", externalDirPresent);

    final File[] list = myAgentWorkDir.listFiles(new FilenameFilter() {
      public boolean accept(final File dir, final String name) {
        return name.endsWith(".snapshot");
      }
    });
    assertEquals(2, list.length);
  }

  public void testEnforceNestedCheckoutDir() throws Exception {
    final File checkoutDir1 = new File(myAgentWorkDir, "checkoutDir1");
    checkoutDir1.mkdirs();
    final File dir2 = new File(checkoutDir1, "dir2");
    dir2.mkdirs();

    final File file = new File(dir2, "file.txt");


    final List<DirectoryMapItem> items = new ArrayList<DirectoryMapItem>();
    items.add(new DirectoryMapItem("bt1", "build1", checkoutDir1, System.currentTimeMillis(), DirectoryLifeTime.getDefault()));

    final Map<String, String> swabraParams = new HashMap<String, String>();
    swabraParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    swabraParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    swabraParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);

    swabraParams.put(SwabraUtil.RULES, "-:" + checkoutDir1.getAbsolutePath() + "\n" +
                                       "+:" + dir2.getAbsolutePath() + "");

    final StringBuilder resultsStrict = new StringBuilder();
    final SimpleBuildLogger loggerStrict = new BuildProgressLoggerMock(resultsStrict);
    final StringBuilder resultsNotStrict = new StringBuilder();
    final SimpleBuildLogger loggerNotStrict = new BuildProgressLoggerMock(resultsNotStrict);

    doWholeTest(items, new Action<Pair<Swabra, SwabraPropertiesProcessor>>() {
      public void apply(final Pair<Swabra, SwabraPropertiesProcessor> pair) {
        try {
          final StringBuilder dummyResults = new StringBuilder();
          final SimpleBuildLogger logger = new BuildProgressLoggerMock(dummyResults);
          final Swabra swabra = pair.getFirst();
          final AgentRunningBuild build = createBuild(swabraParams, checkoutDir1, logger);
          swabra.buildStarted(build);
          dir2.mkdirs();
          file.createNewFile();
          swabra.sourcesUpdated(build);
          file.delete();
          swabra.afterAtrifactsPublished(build, BuildFinishedStatus.FINISHED_SUCCESS);
          swabra.buildFinished(build, BuildFinishedStatus.FINISHED_SUCCESS);

          final AgentRunningBuild build2 = createBuild(swabraParams, checkoutDir1, loggerStrict);
          swabra.buildStarted(build2);
          dir2.mkdirs();
          file.createNewFile();
          swabra.sourcesUpdated(build2);
          file.delete();
          swabra.afterAtrifactsPublished(build2, BuildFinishedStatus.FINISHED_SUCCESS);
          swabra.buildFinished(build2, BuildFinishedStatus.FINISHED_SUCCESS);

          swabraParams.remove(SwabraUtil.STRICT);

          final AgentRunningBuild build3 = createBuild(swabraParams, checkoutDir1, loggerNotStrict);
          swabra.buildStarted(build3);
          dir2.mkdirs();
          file.createNewFile();
          swabra.sourcesUpdated(build3);
          file.delete();
          swabra.afterAtrifactsPublished(build3, BuildFinishedStatus.FINISHED_SUCCESS);
          swabra.buildFinished(build3, BuildFinishedStatus.FINISHED_SUCCESS);

        } catch (Exception e) {
          e.printStackTrace();
          fail(e.toString());
        }
      }
    });
    {
      final String strictLog = resultsStrict.toString();
      assertTrue(strictLog.contains("Detected deleted " + file.getAbsolutePath()));
      assertTrue(strictLog.contains("Checkout directory contains modified files or some files were deleted. " +
                                    "Need a clean checkout directory snapshot - forcing clean checkout"));
    }
    {
      final String notStrictLog = resultsNotStrict.toString();
      assertTrue(notStrictLog.contains("Detected deleted " + file.getAbsolutePath()));
      assertTrue(notStrictLog.contains("Clean checkout not enforced - \"Force clean checkout if cannot restore clean directory state\" is unchecked"));
    }
  }

  public void testStrictForExternalDir() throws Exception {
    final File checkoutDir1 = new File(myAgentWorkDir, "checkoutDir1");
    checkoutDir1.mkdirs();
    final File externalDir = myTempFiles.createTempDir();
    externalDir.mkdirs();

    final File file = new File(externalDir, "file.txt");


    final List<DirectoryMapItem> items = new ArrayList<DirectoryMapItem>();
    items.add(new DirectoryMapItem("bt1", "build1", checkoutDir1, System.currentTimeMillis(), DirectoryLifeTime.getDefault()));

    final Map<String, String> swabraParams = new HashMap<String, String>();
    swabraParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    swabraParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    swabraParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);

    swabraParams.put(SwabraUtil.RULES, "-:" + checkoutDir1.getAbsolutePath() + "\n" +
                                       "+:" + externalDir.getAbsolutePath() + "");

    final StringBuilder resultsStrict = new StringBuilder();
    final SimpleBuildLogger loggerStrict = new BuildProgressLoggerMock(resultsStrict);
    final StringBuilder resultsNotStrict = new StringBuilder();
    final SimpleBuildLogger loggerNotStrict = new BuildProgressLoggerMock(resultsNotStrict);

    doWholeTest(items, new Action<Pair<Swabra, SwabraPropertiesProcessor>>() {
      public void apply(final Pair<Swabra, SwabraPropertiesProcessor> pair) {
        try {
          final StringBuilder dummyResults = new StringBuilder();
          final SimpleBuildLogger logger = new BuildProgressLoggerMock(dummyResults);
          final Swabra swabra = pair.getFirst();
          final AgentRunningBuild build = createBuild(swabraParams, checkoutDir1, logger);
          swabra.buildStarted(build);
          file.createNewFile();
          swabra.sourcesUpdated(build);
          file.delete();
          swabra.afterAtrifactsPublished(build, BuildFinishedStatus.FINISHED_SUCCESS);
          swabra.buildFinished(build, BuildFinishedStatus.FINISHED_SUCCESS);

          final AgentRunningBuild build2 = createBuild(swabraParams, checkoutDir1, loggerStrict);
          swabra.buildStarted(build2);
          file.createNewFile();
          swabra.sourcesUpdated(build2);
          file.delete();
          swabra.afterAtrifactsPublished(build2, BuildFinishedStatus.FINISHED_SUCCESS);
          swabra.buildFinished(build2, BuildFinishedStatus.FINISHED_SUCCESS);

          swabraParams.remove(SwabraUtil.STRICT);

          final AgentRunningBuild build3 = createBuild(swabraParams, checkoutDir1, loggerNotStrict);
          swabra.buildStarted(build3);
          file.createNewFile();
          swabra.sourcesUpdated(build3);
          file.delete();
          swabra.afterAtrifactsPublished(build3, BuildFinishedStatus.FINISHED_SUCCESS);
          swabra.buildFinished(build3, BuildFinishedStatus.FINISHED_SUCCESS);

        } catch (Exception e) {
          e.printStackTrace();
          fail(e.toString());
        }
      }
    });
    {
      final String strictLog = resultsStrict.toString();
      assertTrue(strictLog.contains("Detected deleted " + file.getAbsolutePath()));
      assertFalse(strictLog.contains("Checkout directory contains modified files or some files were deleted. " +
                                     "Need a clean checkout directory snapshot - forcing clean checkout"));
      assertFalse(strictLog.contains("Clean checkout not enforced - \"Force clean checkout if cannot restore clean directory state\" is unchecked"));
    }
    {
      final String notStrictLog = resultsNotStrict.toString();
      assertTrue(notStrictLog.contains("Detected deleted " + file.getAbsolutePath()));
      assertFalse(notStrictLog.contains("Checkout directory contains modified files or some files were deleted. " +
                                     "Need a clean checkout directory snapshot - forcing clean checkout"));
      assertFalse(notStrictLog.contains("Clean checkout not enforced - \"Force clean checkout if cannot restore clean directory state\" is unchecked"));
    }

  }

  //TW-40391
  // The test actually checks whether swabra properly calls AgentLifeCycleAdapter#checkoutDirectoryRemoved
  // to ensure that agent will handle clean checkout properly, if swabra cleanup fails for any reason.
  public void testShouldRaiseCheckoutDirectoryRemovedEvent() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.STRICT, SwabraUtil.TRUE);
    final Map<File, AtomicBoolean> removedCheckoutDirs = new HashMap<File, AtomicBoolean>();
    final AtomicBoolean shouldFail = new AtomicBoolean(false);
    final AtomicReference<String> failMessage = new AtomicReference<String>();
    myDispatcher.addListener(new AgentLifeCycleAdapter(){
      @Override
      public void checkoutDirectoryRemoved(@NotNull final File checkoutDir) {
        if (removedCheckoutDirs.get(checkoutDir) == null || !removedCheckoutDirs.get(checkoutDir).compareAndSet(false, true)) {
          shouldFail.set(true);
          failMessage.set("Removed unexpected dir " + checkoutDir.getAbsolutePath());
        }
      }
    });
    final File checkoutDir1 = new File(myAgentWorkDir, "checkoutDir1");
    checkoutDir1.mkdirs();
    final File externalDir = myTempFiles.createTempDir();
    externalDir.mkdirs();
    final List<DirectoryMapItem> items = new ArrayList<DirectoryMapItem>();
    items.add(new DirectoryMapItem("bt1", "build1", checkoutDir1, System.currentTimeMillis(), DirectoryLifeTime.getDefault()));

    final StringBuilder results = new StringBuilder();

    final SimpleBuildLogger logger = new BuildProgressLoggerMock(results);
    checkoutDir1.mkdirs();
    final File file = new File(checkoutDir1, "file.txt");
    final Random r = new Random();
    removedCheckoutDirs.put(checkoutDir1, new AtomicBoolean(true));
    doWholeTest(items, new Action<Pair<Swabra, SwabraPropertiesProcessor>>() {
      public void apply(final Pair<Swabra, SwabraPropertiesProcessor> pair) {
        try {
          final Swabra swabra = pair.getFirst();
          for (int i=0; i<2; i++) {
            final AgentRunningBuild build = createBuild(firstCallParams, checkoutDir1, logger);
            removedCheckoutDirs.get(checkoutDir1).set(false);
            swabra.buildStarted(build);
            checkoutDir1.mkdirs();
            file.createNewFile();
            swabra.sourcesUpdated(build);
            final int arrayLength = r.nextInt(65536);
            final char[] data = new char[arrayLength];
            Arrays.fill(data, 'A');
            FileUtil.writeFileAndReportErrors(file, new String(data));
            swabra.afterAtrifactsPublished(build, BuildFinishedStatus.FINISHED_SUCCESS);
            swabra.buildFinished(build, BuildFinishedStatus.FINISHED_SUCCESS);
            assertTrue("step: " + (i+1), removedCheckoutDirs.get(checkoutDir1).get());
          }
        } catch (IOException e) {
          e.printStackTrace();
          fail(e.toString());
        }
      }
    });
    if (shouldFail.get()){
      fail(failMessage.get());
    }
    for (final File dir : removedCheckoutDirs.keySet()) {
      if (!removedCheckoutDirs.get(dir).get()){
        fail(String.format("%s should have been removed!", dir.getAbsolutePath()));
      }
    }
  }

  //TW-53881
  public void testShouldCheckForLockingProcessesIfDontCheckFiles() throws Exception {
    if (!SystemInfo.isWindows)
      throw new SkipException("This test is for Windows only");

    final String goldFile = getTestDataPath("emptyCheckoutDir_b" + ".gold", null);
    final String resultsFile = goldFile.replace(".gold", ".tmp");
    System.setProperty(Swabra.TEST_LOG, resultsFile);

    new File(resultsFile).delete();

    File handleExeDir = myTempFiles.createTempDir();
    new File(handleExeDir, "handle.exe").createNewFile();

    myResults = new StringBuilder();

    final SimpleBuildLogger logger = new BuildProgressLoggerMock(myResults);
    final SwabraLogger swabraLogger = new SwabraLogger();
    final DirectoryCleaner directoryCleaner = new DirectoryCleaner() {
      public boolean delete(@NotNull final File f, @NotNull final DirectoryCleanerCallback callback) {
        return false;
      }

      public boolean deleteNow(@NotNull final File f, @NotNull final DirectoryCleanerCallback callback) {
        return false;
      }
    };

    final Swabra swabra = new Swabra(myDispatcher, new SwabraLogger(),
                                     new SwabraPropertiesProcessor(myDispatcher, swabraLogger,
                                                                   new DirectoryMapPersistanceImpl(myAgentConf, new SystemTimeService())),
                                     new BundledToolsRegistry() {
                                       @Nullable
                                       public BundledTool findTool(@NotNull final String name) {
                                         if ("SysinternalsHandle".equalsIgnoreCase(name)) {
                                           return () -> handleExeDir;
                                         }
                                         return null;
                                       }

                                       @Override
                                       public void registerTool(@NotNull final String toolName, @NotNull final BundledTool tool) {

                                       }
                                     },
                                     new DirectoryMapDirectoriesCleanerImpl(myDispatcher,
                                                                            directoryCleaner,
                                                                            new DirectoryMapPersistanceImpl(myAgentConf, new SystemTimeService()),
                                                                            new DirectoryMapDirtyTrackerImpl()),
                                     new LockedFileResolver.LockingProcessesProviderFactory() {
                                       @Nullable
                                       @Override
                                       public LockedFileResolver.LockingProcessesProvider createProvider(final SwabraSettings swabraSettings) {
                                         return new LockedFileResolver.LockingProcessesProvider() {
                                           @NotNull
                                           @Override
                                           public Collection<ProcessInfo> getLockingProcesses(@NotNull final File f) {
                                             return Arrays.asList(new ProcessInfo(1234567l, "SwabraTestProcess"));
                                           }
                                         };
                                       }
                                     }
    );

//    final File pttTemp = new File(TEST_DATA_PATH, "ptt");
//    System.setProperty(ProcessTreeTerminator.TEMP_PATH_SYSTEM_PROPERTY, pttTemp.getAbsolutePath());


    myDispatcher.getMulticaster().afterAgentConfigurationLoaded(myAgent);
    myDispatcher.getMulticaster().agentStarted(myAgent);

    final String checkoutDirPath = myCheckoutDir.getAbsolutePath();

    final Map<String, String> swabraParams = new HashMap<String, String>();
    swabraParams.put(SwabraUtil.ENABLED, "");
    swabraParams.put(SwabraUtil.LOCKING_PROCESS, "report");

    final AgentRunningBuild build = createBuild(swabraParams, myCheckoutDir, logger);
    final BuildRunnerContext runner = myContext.mock(BuildRunnerContext.class, "context"+System.currentTimeMillis());

    runBuild("emptyCheckoutDir", myDispatcher, build, runner, checkoutDirPath);

    System.out.println(myResults.toString());
    assertTrue(myResults.toString().contains("WARNING: Found process locking files in directory"));
    assertTrue(myResults.toString().contains("PID: 1234567"));
    assertTrue(myResults.toString().contains("SwabraTestProcess"));
  }

  private void doWholeTest(final List<DirectoryMapItem> items,
                           final Action<Pair<Swabra, SwabraPropertiesProcessor>
                             > action) throws Exception {
    final DirectoryCleaner directoryCleaner = new DirectoryCleaner() {
      public boolean delete(@NotNull final File f, @NotNull final DirectoryCleanerCallback callback) {
        final FileRemover remover = new FileRemover(callback);
        return remover.doDelete(f, true);
      }

      public boolean deleteNow(@NotNull final File f, @NotNull final DirectoryCleanerCallback callback) {
        return delete(f, callback);
      }
    };

    DirectoryMapPersistance persistance = new DirectoryMapPersistanceImpl(myAgentConf, new SystemTimeService());
    persistance.withDirectoriesMap(new DirectoryMapAction() {
      public void action(@NotNull final DirectoryMapStructure data) {
        for (DirectoryMapItem item : items) {
          data.update(item);
        }
      }
    });
    final SwabraLogger swabraLogger = new SwabraLogger();
    final SwabraPropertiesProcessor propertiesProcessor =
      new SwabraPropertiesProcessor(myDispatcher, swabraLogger, new DirectoryMapPersistanceImpl(myAgentConf, new SystemTimeService()));
    final DirectoryMapDirtyTrackerImpl dirtyTracker = new DirectoryMapDirtyTrackerImpl();
    final Swabra swabra = new Swabra(myDispatcher, new SwabraLogger(),
                                     propertiesProcessor,
                                     new BundledToolsRegistry() {
                                       public BundledTool findTool(@NotNull final String name) {
                                         return null;
                                       }

                                       @Override
                                       public void registerTool(@NotNull final String toolName, @NotNull final BundledTool tool) {

                                       }
                                     },
                                     new DirectoryMapDirectoriesCleanerImpl(
                                       myDispatcher,
                                       directoryCleaner,
                                       persistance,
                                       dirtyTracker
                                       ));


    propertiesProcessor.afterAgentConfigurationLoaded(myAgent);
    propertiesProcessor.agentStarted(myAgent);
    new WaitFor(1000){
      @Override
      protected boolean condition() {
        return propertiesProcessor.isInitialized();
      }
    };

    action.apply(new Pair<Swabra, SwabraPropertiesProcessor>(swabra, propertiesProcessor));
  }

  private void prepareEmptyCheckoutDir() throws Exception {
    File testData = getTestData(null, null);

    new File(testData + File.separator + "emptyCheckoutDir", BEFORE_BUILD).mkdirs();
    new File(testData + File.separator + "emptyCheckoutDir", AFTER_BUILD).mkdirs();
    new File(testData + File.separator + "emptyCheckoutDir", AFTER_CHECKOUT).mkdirs();
  }

  private void prepareOneDeletedDir() throws Exception {
    File testData = getTestData(null, null);

    new File(testData + File.separator + "oneDeleted", BEFORE_BUILD).mkdirs();
    new File(testData + File.separator + "oneDeleted", AFTER_BUILD).mkdirs();
  }
}
