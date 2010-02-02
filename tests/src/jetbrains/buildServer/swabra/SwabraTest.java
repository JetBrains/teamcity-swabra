/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import junit.framework.TestCase;

import java.io.*;
import java.util.Map;
import java.util.HashMap;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.After;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import static jetbrains.buildServer.swabra.TestUtil.*;


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
  private BuildAgentConfiguration myBuildAgentConf;
  private Mockery myContext;


  private AgentRunningBuild createAgentRunningBuild(@NotNull final Map<String, String> runParams,
                                                    @NotNull final BuildAgentConfiguration conf,
                                                    @NotNull final File checkoutDir,
                                                    @NotNull final SimpleBuildLogger logger) {
    final AgentRunningBuild runningBuild = myContext.mock(AgentRunningBuild.class);
    setRunningBuildParams(runParams, conf, checkoutDir, logger, runningBuild);
    return runningBuild;
  }

  private void setRunningBuildParams(@NotNull final Map<String, String> runParams,
                                     @NotNull final BuildAgentConfiguration conf,
                                     @NotNull final File checkoutDir,
                                     @NotNull final SimpleBuildLogger logger,
                                     @NotNull final AgentRunningBuild runningBuild) {
    myContext.checking(new Expectations() {
      {
        oneOf(runningBuild).getRunnerParameters();
        will(returnValue(runParams));
        oneOf(runningBuild).getBuildLogger();
        will(returnValue(logger));
        oneOf(runningBuild).getAgentConfiguration();
        will(returnValue(conf));
        oneOf(runningBuild).getCheckoutDirectory();
        will(returnValue(checkoutDir));
        allowing(runningBuild).isCleanBuild();
        will(returnValue(false));
      }
    });
  }

  private BuildAgentConfiguration createBuildAgentConf(@NotNull final File cachesDir) {
    final BuildAgentConfiguration conf = myContext.mock(BuildAgentConfiguration.class);
    myContext.checking(new Expectations() {
      {
        allowing(conf).getCacheDirectory(with(Swabra.CACHE_KEY));
        will(returnValue(cachesDir));
      }
    });
    return conf;
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

  @Before
  public void setUp() throws Exception {
    myContext = new JUnit4Mockery();
    myTempFiles = new TempFiles();
    myCheckoutDir = new File(myTempFiles.createTempDir(), "checkout_dir");
    myCheckoutDir.mkdirs();
    myBuildAgentConf = createBuildAgentConf(myCheckoutDir.getParentFile());
  }

  @After
  public void tearDown() throws Exception {
    myTempFiles.cleanup();
    super.tearDown();
  }

  private void runTest(final String dirName, final String resultsFileName,
                       final Map<String, String> firstCallParams,
                       final Map<String, String> secondCallParams) throws Exception {
    System.out.println("CheckoutDir is " + myCheckoutDir);
    System.setProperty(Swabra.DISABLE_DOWNLOAD_HANDLE, "True");

    final String goldFile = getTestDataPath(resultsFileName + ".gold", null);
    final String resultsFile = goldFile.replace(".gold", ".tmp");
    System.setProperty(Swabra.TEST_LOG, resultsFile);
//    final String snapshotFile = goldFile.replace(".gold", ".snapshot");

    new File(resultsFile).delete();

    final StringBuilder results = new StringBuilder();

    final SimpleBuildLogger logger = new BuildProgressLoggerMock(results);
    final EventDispatcher<AgentLifeCycleListener> dispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
    final AgentRunningBuild build = createAgentRunningBuild(firstCallParams, myBuildAgentConf, myCheckoutDir, logger);
    final Swabra swabra = new Swabra(dispatcher, createSmartDirectoryCleaner());

    final String checkoutDirPath = myCheckoutDir.getAbsolutePath();

    FileUtil.copyDir(getTestData(dirName + File.separator + BEFORE_BUILD, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().buildStarted(build);
    Thread.sleep(100);
    cleanCheckoutDir();

    FileUtil.copyDir(getTestData(dirName + File.separator + AFTER_CHECKOUT, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().beforeRunnerStart(build);
    dispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    Thread.sleep(100);
    cleanCheckoutDir();

    FileUtil.copyDir(getTestData(dirName + File.separator + AFTER_BUILD, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().buildFinished(BuildFinishedStatus.FINISHED_SUCCESS);
    Thread.sleep(100);

    myContext.assertIsSatisfied();
    setRunningBuildParams(secondCallParams, myBuildAgentConf, myCheckoutDir, logger, build);

    FileUtil.copyDir(getTestData(dirName + File.separator + BEFORE_BUILD, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().buildStarted(build);
    Thread.sleep(100);
    cleanCheckoutDir();

    FileUtil.copyDir(getTestData(dirName + File.separator + AFTER_CHECKOUT, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().beforeRunnerStart(build);
    dispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    Thread.sleep(100);
    cleanCheckoutDir();

    FileUtil.copyDir(getTestData(dirName + File.separator + AFTER_BUILD, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().buildFinished(BuildFinishedStatus.FINISHED_SUCCESS);
    Thread.sleep(100);

    myContext.assertIsSatisfied();

    System.out.println(results.toString().trim());
    //final String actual = results.toString().replace(myCheckoutDir.getAbsolutePath(), "##CHECKOUT_DIR##").replace(checkoutDirParent + File.separator + myCheckoutDir.hashCode() + ".snapshot", "##SNAPSHOT##").trim();
    final String actual = readFile(new File(resultsFile)).replace(myCheckoutDir.getAbsolutePath(), "##CHECKOUT_DIR##").trim();
    final String expected = readFile(new File(goldFile)).trim();
    if (!actual.equals(expected)) {
      final FileWriter resultsWriter = new FileWriter(resultsFile);
      resultsWriter.write(actual);
      resultsWriter.close();

      assertEquals(actual, expected, actual);
    }

//    final File actualSnapshotf = new File(myCheckoutDir.getAbsolutePath() + ".snapshot");
//    if (actualSnapshotf.exists()) {
//      final File goldSnapshotf = new File(snapshotFile);
//      String actualSnapshot = readFile(actualSnapshotf).replace(myCheckoutDir.getParent(), "##WORK_DIR##").replace(myCheckoutDir.getName(), "##CHECKOUT_DIR##").trim();
//      String expectedSnapshot = readFile(goldSnapshotf).trim();
//      if (!actualSnapshot.equals(expectedSnapshot)) {
//        FileUtil.copy(actualSnapshotf, new File(goldSnapshotf.getAbsolutePath() + ".tmp"));
//
//        assertEquals(actualSnapshot, expectedSnapshot, actualSnapshot);
//      }
//    }
  }

  private void cleanCheckoutDir() {
    final File[] files = myCheckoutDir.listFiles();
    if (files != null && files.length != 0) {
      for (File file : files) {
        FileUtil.delete(file);
      }
    }
  }

  public void testEmptyCheckoutDirBeforeBuild() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.MODE, SwabraUtil.BEFORE_BUILD);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.MODE, SwabraUtil.BEFORE_BUILD);

    runTest("emptyCheckoutDir", "emptyCheckoutDir_b", firstCallParams, secondCallParams);
  }  

  public void testEmptyCheckoutDirAfterBuild() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.MODE, SwabraUtil.AFTER_BUILD);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.MODE, SwabraUtil.AFTER_BUILD);

    runTest("emptyCheckoutDir", "emptyCheckoutDir_a", firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedBeforeBuild() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.MODE, SwabraUtil.BEFORE_BUILD);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.MODE, SwabraUtil.BEFORE_BUILD);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_b",
              firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedAfterBuild() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.MODE, SwabraUtil.AFTER_BUILD);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.MODE, SwabraUtil.AFTER_BUILD);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_a",
            firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedBeforeAfter() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.MODE, SwabraUtil.BEFORE_BUILD);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.MODE, SwabraUtil.AFTER_BUILD);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_b_a",
            firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedAfterBefore() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    firstCallParams.put(SwabraUtil.MODE, SwabraUtil.AFTER_BUILD);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.MODE, SwabraUtil.BEFORE_BUILD);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_a_b",
            firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedTurnedOffBefore() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.MODE, SwabraUtil.BEFORE_BUILD);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_off_b",
            firstCallParams, secondCallParams);
  }

  public void testOneCreatedOneModifiedOneNotChangedTurnedOffAfter() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.MODE, SwabraUtil.AFTER_BUILD);

    runTest("oneCreatedOneModifiedOneNotChanged", "oneCreatedOneModifiedOneNotChanged_off_a",
            firstCallParams, secondCallParams);
  }

  public void testOneDeletedAfterBuild() throws Exception {
    final Map<String, String> firstCallParams = new HashMap<String, String>();
    firstCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);

    final Map<String, String> secondCallParams = new HashMap<String, String>();
    secondCallParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    secondCallParams.put(SwabraUtil.MODE, SwabraUtil.AFTER_BUILD);

    runTest("oneDeleted", "oneDeleted_a",
            firstCallParams, secondCallParams);
  }
}
