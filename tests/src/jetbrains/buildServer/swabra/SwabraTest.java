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


/**
 * User: vbedrosova
 * Date: 21.04.2009
 * Time: 15:03:19
 */
public class SwabraTest extends TestCase {
  private static final String BEFORE_BUILD = "beforeBuild";
  private static final String AFTER_CHECKOUT = "afterCheckout";
  private static final String AFTER_BUILD = "afterBuild";

  private static final String TEST_DATA_PATH = "tests" + File.separator + "testData";

  private BuildParametersMap myBuildParams;
  private File myCheckoutDir;
  private TempFiles myTempFiles;
  private Mockery myContext;


  private AgentRunningBuild createAgentRunningBuild(final Map<String, String> runParams, final BuildParametersMap buildParams, final File checkoutDir, final SimpleBuildLogger logger) {
    final AgentRunningBuild runningBuild = myContext.mock(AgentRunningBuild.class);
    setRunningBuildParams(runParams, buildParams, checkoutDir, logger, runningBuild);
    return runningBuild;
  }

  private void setRunningBuildParams(final Map<String, String> runParams, final BuildParametersMap buildParams, final File checkoutDir, final SimpleBuildLogger logger, final AgentRunningBuild runningBuild) {
    myContext.checking(new Expectations() {
      {
        oneOf(runningBuild).getBuildLogger();
        will(returnValue(logger));
        oneOf(runningBuild).getRunnerParameters();
        will(returnValue(runParams));
        allowing(runningBuild).getBuildParameters();
        will(returnValue(buildParams));
        oneOf(runningBuild).getCheckoutDirectory();
        will(returnValue(checkoutDir));
        allowing(runningBuild).isCleanBuild();
        will(returnValue(false));
      }
    });
  }

  private BuildParametersMap createBuildParametersMap(final Map<String, String> buildParams) {
    final BuildParametersMap params = myContext.mock(BuildParametersMap.class);
    myContext.checking(new Expectations() {
      {
        allowing(params).getSystemProperties();
        will(returnValue(buildParams));
      }
    });
    return params;
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

  private void setAgentRunningBuildParams(final AgentRunningBuild runningBuild, final Map<String, String> runParams,
                                          BuildParametersMap buildParams,
                                          final File checkoutDir, final SimpleBuildLogger logger) {
    setRunningBuildParams(runParams, buildParams, checkoutDir, logger, runningBuild);
  }

  @Before
  public void setUp() throws IOException {
    myContext = new JUnit4Mockery();
    myTempFiles = new TempFiles();
    myCheckoutDir = myTempFiles.createTempDir();
    final Map<String, String> buildParams =  new HashMap<String, String>();
    buildParams.put(Swabra.WORK_DIR_PROP, myCheckoutDir.getParent());
    myBuildParams = createBuildParametersMap(buildParams);
  }

  @After
  public void tearDown() throws Exception {
    myTempFiles.cleanup();
    super.tearDown();
  }

  private String getTestDataPath(final String fileName, final String folderName) throws Exception {
    return getTestData(fileName, folderName).getAbsolutePath();
  }

  private File getTestData(final String fileName, final String folderName) throws Exception {
    final String relativeFileName = TEST_DATA_PATH + (folderName != null ? File.separator + folderName : "") + (fileName != null ? File.separator + fileName : "");
    final File file1 = new File(relativeFileName);
    if (file1.exists()) {
      return file1;
    }
    final File file2 = new File("svnrepo" + File.separator + "swabra" + File.separator + relativeFileName);
    if (file2.exists()) {
      return file2;
    }
    throw new FileNotFoundException("Either " + file1.getAbsolutePath() + " or file " + file2.getAbsolutePath() + " should exist.");
  }

  static private String readFile(@NotNull final File file) throws IOException {
    final FileInputStream inputStream = new FileInputStream(file);
    try {
      final BufferedInputStream bis = new BufferedInputStream(inputStream);
      final byte[] bytes = new byte[(int)file.length()];
      bis.read(bytes);
      bis.close();

      return new String(bytes);
    }
    finally {
      inputStream.close();
    }
  }

  private void runTest(final String dirName, final String resultsFileName,
                       final Map<String, String> firstCallParams,
                       final Map<String, String> secondCallParams) throws Exception {
    final String goldFile = getTestDataPath(resultsFileName + ".gold", null);
    final String resultsFile = goldFile.replace(".gold", ".tmp");
//    final String snapshotFile = goldFile.replace(".gold", ".snapshot");

    new File(resultsFile).delete();

    final StringBuilder results = new StringBuilder();

    final SimpleBuildLogger logger = new BuildProgressLoggerMock(results);
    final EventDispatcher<AgentLifeCycleListener> dispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
    final AgentRunningBuild build = createAgentRunningBuild(firstCallParams, myBuildParams, myCheckoutDir, logger);
    final Swabra swabra = new Swabra(dispatcher, createSmartDirectoryCleaner());

    final String checkoutDirPath = myCheckoutDir.getAbsolutePath();

    FileUtil.copyDir(getTestData(dirName + File.separator + BEFORE_BUILD, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().buildStarted(build);
    cleanCheckoutDir();

    FileUtil.copyDir(getTestData(dirName + File.separator + AFTER_CHECKOUT, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().beforeRunnerStart(build);
    dispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    cleanCheckoutDir();

    FileUtil.copyDir(getTestData(dirName + File.separator + AFTER_BUILD, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().buildFinished(BuildFinishedStatus.FINISHED_SUCCESS);

    myContext.assertIsSatisfied();
    setAgentRunningBuildParams(build, secondCallParams, myBuildParams, myCheckoutDir, logger);

    FileUtil.copyDir(getTestData(dirName + File.separator + BEFORE_BUILD, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().buildStarted(build);
    cleanCheckoutDir();

    FileUtil.copyDir(getTestData(dirName + File.separator + AFTER_CHECKOUT, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().beforeRunnerStart(build);
    dispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    cleanCheckoutDir();

    FileUtil.copyDir(getTestData(dirName + File.separator + AFTER_BUILD, null), myCheckoutDir);
    FileUtil.delete(new File(checkoutDirPath + File.separator + ".svn"));
    dispatcher.getMulticaster().buildFinished(BuildFinishedStatus.FINISHED_SUCCESS);

    myContext.assertIsSatisfied();

    final String checkoutDir = myCheckoutDir.getCanonicalPath();

    final File goldf = new File(goldFile);
    final String actual = results.toString().replace(checkoutDir, "##BASE_DIR##").trim();
    final String expected = readFile(goldf).trim();
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
      for (int i = 0; i < files.length; ++i) {
        FileUtil.delete(files[i]);
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
}
