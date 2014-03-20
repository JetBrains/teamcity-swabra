/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.swabra.snapshots.iteration.FileInfo;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import org.junit.Before;

import static jetbrains.buildServer.swabra.TestUtil.getTestData;
import static jetbrains.buildServer.swabra.TestUtil.getTestDataPath;


/**
 * User: vbedrosova
 * Date: 21.04.2009
 * Time: 15:03:19
 */
public class SwabraTest extends TestCase {
  private static final String BEFORE_BUILD = "beforeBuild";
  private static final String AFTER_CHECKOUT = "afterCheckout";
  private static final String AFTER_BUILD = "afterBuild";

  private File myCheckoutDir;
  private TempFiles myTempFiles;
  private Mockery myContext;


  private AgentRunningBuild createBuild(@NotNull final Map<String, String> runParams,
                                        @NotNull final File checkoutDir,
                                        @NotNull final SimpleBuildLogger logger) {
    final AgentRunningBuild build = myContext.mock(AgentRunningBuild.class);

    myContext.checking(new Expectations() {
      {
        allowing(build).getBuildFeaturesOfType(with("swabra"));
        will(returnValue(createBuildFeatures(runParams)));
        allowing(build).getBuildLogger();
        will(returnValue(logger));
        allowing(build).getCheckoutDirectory();
        will(returnValue(checkoutDir));
        allowing(build).isCleanBuild();
        will(returnValue(false));
        allowing(build).getSharedConfigParameters();
        will(returnValue(Collections.emptyMap()));
        allowing(build).isCheckoutOnAgent();
        will(returnValue(false));
        allowing(build).isCheckoutOnServer();
        will(returnValue(true));
        allowing(build).getInterruptReason();
        will(returnValue(null));
      }
    });

    return build;
  }

  @NotNull
  private Collection<AgentBuildFeature> createBuildFeatures(@NotNull final Map<String, String> runParams) {
    return Collections.<AgentBuildFeature>singletonList(new AgentBuildFeature() {
      @NotNull
      public String getType() {
        return "swabra";
      }

      @NotNull
      public Map<String, String> getParameters() {
        return runParams;
      }
    });
  }

  private BuildAgentConfiguration createBuildAgentConf(@NotNull final File cachesDir) {
    final BuildAgentConfiguration conf = myContext.mock(BuildAgentConfiguration.class);
    myContext.checking(new Expectations() {
      {
        allowing(conf).getCacheDirectory(with(Swabra.CACHE_KEY));
        will(returnValue(cachesDir));
        allowing(conf).getWorkDirectory();
        will(returnValue(cachesDir));
      }
    });
    return conf;
  }

  private BuildAgent createBuildAgent(@NotNull final File cachesDir) {
    final BuildAgent agent = myContext.mock(BuildAgent.class);
    myContext.checking(new Expectations() {
      {
        allowing(agent).getConfiguration();
        will(returnValue(createBuildAgentConf(cachesDir)));
      }
    });
    return agent;
  }

  private SmartDirectoryCleaner createSmartDirectoryCleaner() {
    return new SmartDirectoryCleaner() {
      public void cleanFolder(@NotNull File file, @NotNull SmartDirectoryCleanerCallback callback) {
        callback.logCleanStarted(file);
        if (!FileUtil.delete(file)) {
          callback.logFailedToCleanEntireFolder(file);
        }
      }
    };
  }

  @Override
  @Before
  public void setUp() throws Exception {
    myContext = new JUnit4Mockery();
    myTempFiles = new TempFiles();
    myCheckoutDir = new File(myTempFiles.createTempDir(), "checkout_dir");
    myCheckoutDir.mkdirs();
  }

  @Override
  @After
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

    final StringBuilder results = new StringBuilder();

    final SimpleBuildLogger logger = new BuildProgressLoggerMock(results);
    final EventDispatcher<AgentLifeCycleListener> dispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
    final SwabraLogger swabraLogger = new SwabraLogger();
    final Swabra swabra = new Swabra(dispatcher, createSmartDirectoryCleaner(), new SwabraLogger(),
      new SwabraPropertiesProcessor(dispatcher, swabraLogger), new BundledToolsRegistry() {
      public BundledTool findTool(@NotNull final String name) {
        return null;
      }
    }/*, new ProcessTerminator()*/);

//    final File pttTemp = new File(TEST_DATA_PATH, "ptt");
//    System.setProperty(ProcessTreeTerminator.TEMP_PATH_SYSTEM_PROPERTY, pttTemp.getAbsolutePath());


    final BuildAgent agent = createBuildAgent(myCheckoutDir.getParentFile());
    dispatcher.getMulticaster().afterAgentConfigurationLoaded(agent);
    dispatcher.getMulticaster().agentStarted(agent);

    final String checkoutDirPath = myCheckoutDir.getAbsolutePath();

    final Map<String, String> runParams = new HashMap<String, String>();
    final AgentRunningBuild build = createBuild(runParams, myCheckoutDir, logger);
    final BuildRunnerContext runner = myContext.mock(BuildRunnerContext.class);

    for (Map<String, String> param : params) {
      runParams.clear();
      runParams.putAll(param);
      runBuild(dirName, dispatcher, build, runner, checkoutDirPath);
      if (afterBuildActions != null) {
        afterBuildActions.run();
      }
    }

    final String baseText = FileUtil.readText(new File(resultsFile)).trim();
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
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.BEFORE_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("emptyCheckoutDir", "emptyCheckoutDir_b", firstCallParams, secondCallParams);
  }

  public void testEmptyCheckoutDirStrict() throws Exception {
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
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    runTest("oneDeleted", "oneDeleted_a",
      firstCallParams, secondCallParams);
  }

  public void testOneDeletedNonStrict() throws Exception {
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
      final EventDispatcher<AgentLifeCycleListener> dispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
      final SwabraLogger swabraLogger = new SwabraLogger();
      final SwabraPropertiesProcessor propertiesProcessor = new SwabraPropertiesProcessor(dispatcher, swabraLogger) {
        @Override
        public DirectoryState getState(final File dir) {
          return dir.equals(myCheckoutDir) ? DirectoryState.PENDING : DirectoryState.UNKNOWN;
        }

        @Override
        public File getSnapshotFile(final File dir) {
          FileWriter writer  = null;
          File snapshotFile = null;
          try {
            snapshotFile = File.createTempFile("swabraInterrupter", "snapshot");
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

      final Swabra swabra = new Swabra(dispatcher, createSmartDirectoryCleaner(), new SwabraLogger(),
                                       propertiesProcessor, new BundledToolsRegistry() {
        public BundledTool findTool(@NotNull final String name) {
          return null;
        }
      }/*, new ProcessTerminator()*/);

      final CountDownLatch latch = new CountDownLatch(2);
      final AtomicBoolean interruptedFlag = new AtomicBoolean(false);
      final AtomicInteger numberFilesProcessed = new AtomicInteger(0);

      swabra.setInternalProcessor(new FilesCollectionProcessorForTests(swabraLogger, null, myCheckoutDir, true, true, interruptedFlag) {
        @Override
        public boolean willProcess(final FileInfo info) throws InterruptedException {
          numberFilesProcessed.incrementAndGet();
          latch.countDown();
          Thread.sleep(500);
          return super.willProcess(info);
        }
      },
                                  interruptedFlag);

      final BuildAgent agent = createBuildAgent(myCheckoutDir.getParentFile());
      dispatcher.getMulticaster().afterAgentConfigurationLoaded(agent);
      dispatcher.getMulticaster().agentStarted(agent);

      final Map<String, String> runParams = new HashMap<String, String>();
      final AgentRunningBuild build = createBuild(runParams, myCheckoutDir, logger);

      final Thread interruptThread = new Thread(new Runnable() {
        public void run() {
          try {
            latch.await(10, TimeUnit.SECONDS);
            dispatcher.getMulticaster().beforeBuildInterrupted(build, BuildInterruptReason.SERVER_STOP_BUILD);
          } catch (InterruptedException ignored) {
          }
        }
      });
      interruptThread.start();

      final Map<String, String> map = new HashMap<String, String>();
      map.put(SwabraUtil.ENABLED, SwabraUtil.TRUE);
      runParams.putAll(map);

      dispatcher.getMulticaster().buildStarted(build);

      // processed 2 of 5 files
      assertEquals(2, numberFilesProcessed.get());
    } finally {
      System.getProperties().remove(Swabra.TEST_LOG);
    }
  }

  //public static void main(String[] argx) {
  //  for (String s : Arrays.asList(
  //  "$%'`-@{}~!#()&_^",
  //  "+file",
  //  "-file",
  //  ".File",
  //  "347",
  //  "=][;.,+№",
  //  "=file",
  //  "file name (with brackets)",
  //  "many.ext.en.sion.s",
  //  "restarting@2x.png",
  //  "unicode-äßááóöéÄúü-ыйЁ.пых-ÿ",
  //  "xss_try_%27%22%3Cscript%3Ealert(666)%3B%3C%2Fscript%3E.png",
  //  "файло")) {
  //    StringBuilder sb = new StringBuilder();
  //    for (int idx = 0; idx < s.length(); idx++) {
  //      char ch = s.charAt(idx);
  //      String hexCode = Integer.toHexString(ch);
  //      sb.append("\\u");
  //      int paddingCount = 4 - hexCode.length();
  //      while (paddingCount-- > 0) {
  //        sb.append(0);
  //      }
  //      sb.append(hexCode);
  //    }
  //    System.out.println(s + " " + sb);
  //  }
  //}

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
        if (runCount.incrementAndGet() == 1){
          try {
            tempFileInDir.createNewFile();
          } catch (IOException e) {e.printStackTrace();}
        }
      }
    }, Arrays.asList(tmpNonCheckoutDir), firstCallParams, secondCallParams);

    assertFalse(tempFileInDir.exists());
  }


}
