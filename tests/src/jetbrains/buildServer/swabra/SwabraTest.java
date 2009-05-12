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

  private File myCheckoutDir;
  private TempFiles myTempFiles;
  private Mockery myContext;
  private Map<String, String> myRunParams;


  private AgentRunningBuild createAgentRunningBuild(final Map<String, String> runParams, final File checkoutDir, final SimpleBuildLogger logger) {
    final AgentRunningBuild runningBuild = myContext.mock(AgentRunningBuild.class);
    myContext.checking(new Expectations() {
      {
        allowing(runningBuild).getBuildLogger();
        will(returnValue(logger));
        allowing(runningBuild).getRunnerParameters();
        will(returnValue(runParams));
        allowing(runningBuild).getCheckoutDirectory();
        will(returnValue(checkoutDir));
        ignoring(runningBuild);
      }
    });
    return runningBuild;
  }  

  @Before
  public void setUp() throws IOException {
    myContext = new JUnit4Mockery();
    myTempFiles = new TempFiles();
    myCheckoutDir = myTempFiles.createTempDir();
    myRunParams = new HashMap<String, String>();
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
    final String relativeFileName = "tests/testData" + (folderName != null ? "/" + folderName : "") + (fileName != null ? "/" + fileName : "");
    final File file1 = new File(relativeFileName);
    if (file1.exists()) {
      return file1;
    }
    final File file2 = new File("svnrepo/swabra/" + relativeFileName);
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

  private void runTest(final String dirName, final String resultsFileName) throws Exception {
    final String goldFile = getTestDataPath(resultsFileName + ".gold", null);
    final String resultsFile = goldFile.replace(".gold", ".tmp");

    new File(resultsFile).delete();

    final StringBuilder results = new StringBuilder();

    final SimpleBuildLogger logger = new BuildProgressLoggerMock(results);
    final EventDispatcher<AgentLifeCycleListener> dispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
    final AgentRunningBuild build = createAgentRunningBuild(myRunParams, myCheckoutDir, logger);
    final Swabra swabra = new Swabra(dispatcher);

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

    final File goldf = new File(goldFile);
    String checkoutDir = myCheckoutDir.getCanonicalPath();
    String actual = results.toString().replace(checkoutDir, "##BASE_DIR##").trim();
    String expected = readFile(goldf).trim();
    if (!actual.equals(expected)) {
      final FileWriter resultsWriter = new FileWriter(resultsFile);
      resultsWriter.write(results.toString());
      resultsWriter.close();

      assertEquals(actual, expected, actual);
    }
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
    myRunParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    myRunParams.put(SwabraUtil.MODE, SwabraUtil.BEFORE_BUILD);
    runTest("emptyCheckoutDir", "emptyCheckoutDir_b");
  }  

  public void testEmptyCheckoutDirAfterBuild() throws Exception {
    myRunParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    myRunParams.put(SwabraUtil.MODE, SwabraUtil.AFTER_BUILD);
    runTest("emptyCheckoutDir", "emptyCheckoutDir_a");
  }

  public void testOneFileCreatedBeforeBuild() throws Exception {
    myRunParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    myRunParams.put(SwabraUtil.MODE, SwabraUtil.BEFORE_BUILD);
    runTest("oneFileCreated", "oneFileCreated_b");
  }

  public void testOneFileCreatedAfterBuild() throws Exception {
    myRunParams.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    myRunParams.put(SwabraUtil.MODE, SwabraUtil.AFTER_BUILD);
    runTest("oneFileCreated", "oneFileCreated_a");
  }
}
