/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.util.WaitFor;
import java.io.File;
import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.impl.AgentBuildSettingsProxy;
import jetbrains.buildServer.agent.impl.directories.*;
import jetbrains.buildServer.agentServer.Server;
import jetbrains.buildServer.artifacts.ArtifactDependencyInfo;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.vcs.VcsChangeInfo;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.hamcrest.Description;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Sergey.Pak
 *         Date: 12/5/2014
 *         Time: 3:36 PM
 */
@Test
public class SwabraTest2 extends BaseTestCase {

  private File myCheckoutDir;
  private File mySwabraDir;
  private Swabra mySwabra;
  private final BundledToolsRegistry emptyToolsRegistry = new BundledToolsRegistry() {
    @Nullable
    public BundledTool findTool(@NotNull final String name) {
      return null;
    }
  };
  private EventDispatcher<AgentLifeCycleListener> myAgentDispatcher;
  private SmartDirectoryCleanerImpl myDirectoryCleaner;
  private SwabraLogger mySwabraLogger;
  private SwabraPropertiesProcessor myPropertiesProcessor;
  private Collection<Map<String,String>> mySwabraParamsRef;
  private BuildProgressLogger myBuildProgressLogger;
  private StringBuilder myBuildLog;
  private Mockery myMockery;
  private BuildAgent myAgent;
  private AgentRunningBuild myRunningBuild;
  private BuildAgentConfiguration myAgentConf;
  private List<File> mySourceFiles;
  private List<File> myAddedFiles;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    final File agentWorkDir = createTempDir();
    myCheckoutDir = new File(agentWorkDir, "checkout");
    myCheckoutDir.mkdir();
    mySwabraDir = createTempDir();
    myAgentDispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
    final DirectoryCleaner cleaner = new DirectoryCleaner() {
      public boolean delete(@NotNull final File f, @NotNull final DirectoryCleanerCallback callback) {
        return false;
      }

      public boolean deleteNow(@NotNull final File f, @NotNull final DirectoryCleanerCallback callback) {
        return false;
      }
    };
    myDirectoryCleaner = new SmartDirectoryCleanerImpl(cleaner);
    mySwabraLogger = new SwabraLogger();
    myMockery = new Mockery();
    myAgentConf = myMockery.mock(BuildAgentConfiguration.class);
    myPropertiesProcessor = new SwabraPropertiesProcessor(myAgentDispatcher, mySwabraLogger, new DirectoryMapPersistanceImpl(myAgentConf, new SystemTimeService()));
    mySwabra = new Swabra(myAgentDispatcher, myDirectoryCleaner, mySwabraLogger, myPropertiesProcessor, emptyToolsRegistry,
                          new DirectoryMapDirectoriesCleanerImpl(myAgentDispatcher, cleaner, new DirectoryMapPersistanceImpl(myAgentConf, new SystemTimeService()), new DirectoryMapDirtyTrackerImpl()));

    mySwabraParamsRef = new ArrayList<Map<String, String>>();
    myBuildLog = new StringBuilder();
    myBuildProgressLogger = new BuildProgressLoggerMock(myBuildLog);
    mySwabraLogger.setBuildLogger(myBuildProgressLogger);


    myAgent = myMockery.mock(BuildAgent.class);
    myRunningBuild = myMockery.mock(AgentRunningBuild.class);

    myMockery.checking(new Expectations(){{
      allowing(myAgent).getConfiguration(); will(returnValue(myAgentConf));


      allowing(myRunningBuild).getCheckoutDirectory(); will(returnValue(myCheckoutDir));
      allowing(myRunningBuild).getInterruptReason(); will(returnValue(null));
      allowing(myRunningBuild).getSharedConfigParameters(); will(returnValue(Collections.emptyMap()));
      allowing(myRunningBuild).isCheckoutOnAgent(); will(returnValue(false));
      allowing(myRunningBuild).getBuildLogger(); will(returnValue(myBuildProgressLogger));
      allowing(myRunningBuild).getBuildFeaturesOfType(with("swabra")); will(doAll(new Action() {
        public Object invoke(final Invocation invocation) throws Throwable {
          Collection<AgentBuildFeature> swabraFeatures = new ArrayList<AgentBuildFeature>();
          for (final Map<String, String> map : mySwabraParamsRef) {
            swabraFeatures.add(new AgentBuildFeature() {
              @NotNull
              public String getType() {
                return "swabra";
              }

              @NotNull
              public Map<String, String> getParameters() {
                return map;
              }
            });
          }
          return swabraFeatures;
        }

        public void describeTo(final Description description) {

        }
      }));
      allowing(myRunningBuild).isCleanBuild(); will(returnValue(true));


      allowing(myAgentConf).getCacheDirectory(with("swabra")); will(returnValue(mySwabraDir));
      allowing(myAgentConf).getWorkDirectory(); will(returnValue(agentWorkDir));

    }});
    Map<String, String> swabraprops = new HashMap<String, String>();
    swabraprops.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    swabraprops.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    mySwabraParamsRef.add(swabraprops);

    myPropertiesProcessor.afterAgentConfigurationLoaded(myAgent);
    myPropertiesProcessor.agentStarted(myAgent);
    new WaitFor(1000){
      @Override
      protected boolean condition() {
        return myPropertiesProcessor.isInitialized();
      }
    };
    mySourceFiles = new ArrayList<File>();
    myAddedFiles = new ArrayList<File>();
  }

  private void doTest(@Nullable final ActionThrow<Exception> preparations,
                      @Nullable final ActionThrow<Exception> actions,
                      @Nullable final ActionThrow<Exception> assertions) throws Exception {
    doTest(preparations, actions, assertions, true);
  }
  private void doTest(@Nullable final ActionThrow<Exception> preparations,
                      @Nullable final ActionThrow<Exception> actions,
                      @Nullable final ActionThrow<Exception> assertions,
                      final boolean checkForExceptions) throws Exception {
    mySwabra.buildStarted(myRunningBuild);

    if (preparations != null)
      preparations.apply();

    mySwabra.sourcesUpdated(myRunningBuild);
    if (actions != null)
      actions.apply();

    mySwabra.afterAtrifactsPublished(myRunningBuild, BuildFinishedStatus.FINISHED_SUCCESS);

    if (checkForExceptions){
      final String s = myBuildLog.toString();
      assertNotContains(s, "Exception", false);
    }
    if (assertions != null)
      assertions.apply();
  }


  public void should_not_delete_content_inside_symlink_dir() throws Exception {
    final File dir1 = new File(myCheckoutDir, "dir");
    final File insideFile = new File(dir1, "insideFile");
    final File symlinkFile = new File(myCheckoutDir, "symlinkDir");
    doTest(new ActionThrow<Exception>() {
      public void apply() throws Exception {
        assertTrue(dir1.mkdir());
        assertTrue(insideFile.createNewFile());
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        final boolean symlinkCreated = FileUtil.createSymlink(dir1, symlinkFile);
        if (!symlinkCreated) {
          throw new SkipException("Cannot create symlink on this OS");
        }
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        assertFalse(symlinkFile.exists());
        assertTrue(dir1.exists());
        assertTrue(insideFile.exists());
      }
    });
  }

  public void should_delete_added_dir_at_once() throws Exception {
    final File dir2 = new File(myCheckoutDir, "dir2");
    final File dir2_dir = new File(dir2, "dir2_dir");
    final File dir2_file = new File(dir2, "dir2_file.txt");
    doTest(null, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        assertTrue(dir2.mkdir());
        assertTrue(dir2_dir.mkdir());
        assertTrue(dir2_file.createNewFile());
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        assertFalse(dir2.exists());
        final String[] lines = myBuildLog.toString().split("\\n");
        final String dir2DeleteMessage = String.format("MESSAGE: Detected new and deleted %s", dir2.getAbsolutePath());
        final String dir2DirDeleteMessage = String.format("MESSAGE: Detected new and deleted %s", dir2_dir.getAbsolutePath());
        final String dir2FileDeleteMessage = String.format("MESSAGE: Detected new and deleted %s", dir2_file.getAbsolutePath());
        assertContains(Arrays.asList(lines), dir2DeleteMessage);
        assertNotContains(Arrays.asList(lines), dir2DirDeleteMessage, dir2FileDeleteMessage);
      }
    });
  }

  @DataProvider(name="filesCounts")
  public static Object[][] filesCounts(){
    return new Object[][]{{1},{2}, {1000}};
  }

  @Test(dataProvider = "filesCounts")
  public void should_clean_checkout_dir_all_files(final int filesCount) throws Exception {
    doTest(new ActionThrow<Exception>() {
      public void apply() throws Exception {
        final File[] files = myCheckoutDir.listFiles();
        FileRemover fileRemover = new FileRemover(DirectoryCleanerCallback.EMPTY);
        for (File file : files) {
          assertTrue(fileRemover.doDelete(file, false));
        }
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        for (int i=0; i<filesCount; i++){
          new File(myCheckoutDir, "file"+i+".txt").createNewFile();
        }
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        assertEquals(0, myCheckoutDir.listFiles().length);
        final List<String> strings = Arrays.asList(myBuildLog.toString().split("\\n"));
        for (int i=0; i<filesCount; i++) {
          File file = new File(myCheckoutDir, "file"+i+".txt");
          assertContains(strings, String.format("MESSAGE: Detected new and deleted %s", file.getAbsolutePath()));
          assertFalse(file.exists());
        }
        checkResults(0, 0, filesCount, 0);
      }
    });
  }

  @Test(dataProvider = "filesCounts")
  public void should_clean_subdir_all_files(final int filesCount) throws Exception {
    final File dir1 = new File(myCheckoutDir, "dir1");
    doTest(new ActionThrow<Exception>() {
      public void apply() throws Exception {
        assertTrue(dir1.mkdir());
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        for (int i=0; i<filesCount; i++){
          new File(dir1, "file"+i+".txt").createNewFile();
        }
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        assertEquals(0, dir1.listFiles().length);
        final List<String> strings = Arrays.asList(myBuildLog.toString().split("\\n"));
        for (int i=0; i<filesCount; i++) {
          File file = new File(dir1, "file"+i+".txt");
          assertContains(strings, String.format("MESSAGE: Detected new and deleted %s", file.getAbsolutePath()));
          assertFalse(file.exists());
        }
        checkResults(1, 0, filesCount, 0);
      }
    });
  }


  public void should_clean_files_in_nested_dirs() throws Exception {
    final int count = 3;
    final File newDir = new File(myCheckoutDir,"newDir");
    doTest(new ActionThrow<Exception>() {
      public void apply() throws Exception {
        newDir.mkdir();
        for (int i = 0; i < count; i++) {
          final File level2Dir = new File(newDir, "level2_" + i);
          level2Dir.mkdir();
          for (int j = 0; j < count; j++) {
            final File level3Dir = new File(level2Dir, "level3_" + i + "" + j);
            level3Dir.mkdir();
          }
        }
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        for (int i = 0; i < count; i++) {
          final File level2Dir = new File(newDir, "level2_" + i);
          for (int j = 0; j < count; j++) {
            final File level3Dir = new File(level2Dir, "level3_" + i + "" + j);
            final File file = new File(level3Dir, "file.txt");
            file.createNewFile();
          }
          final File file = new File(level2Dir, "file.txt");
          file.createNewFile();
        }
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        for (int i = 0; i < count; i++) {
          final File level2Dir = new File(newDir, "level2_" + i);
          final File file = new File(level2Dir, "file.txt");
          assertFalse(file.exists());
          assertEquals(count, level2Dir.listFiles().length);
          for (int j = 0; j < count; j++) {
            final File level3Dir = new File(level2Dir, "level3_" + i + "" + j);
            assertEquals(0, level3Dir.listFiles().length);
            final File file2 = new File(level3Dir, "file.txt");
            assertFalse(file2.exists());
          }
          checkResults(13, 0, 12, 0);
        }
      }
    });
  }

  public void should_clean_dir_with_nested_dirs() throws Exception {
    final int count = 3;
    final File newDir = new File(myCheckoutDir,"newDir");
    doTest(new ActionThrow<Exception>() {
      public void apply() throws Exception {
        newDir.mkdir();
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        for (int i = 0; i < count; i++) {
          final File level2Dir = new File(newDir, "level2_" + i);
          level2Dir.mkdir();
          for (int j = 0; j < count; j++) {
            final File level3Dir = new File(level2Dir, "level3_" + i + "" + j);
            level3Dir.mkdir();
            final File file = new File(level3Dir, "file.txt");
            file.createNewFile();
          }
          final File file = new File(level2Dir, "file.txt");
          file.createNewFile();
        }
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        assertEquals(0, newDir.listFiles().length);
        checkResults(1, 0, 3, 0);
    }});
  }

  public void should_process_deleted_dir_as_one_entry() throws Exception {
    final int count=3;
    final File baseDir = new File(myCheckoutDir, "baseDir");

    doTest(new ActionThrow<Exception>() {
      public void apply() throws Exception {
        baseDir.mkdir();
        for (int i = 0; i < count; i++) {
          final File level2Dir = new File(baseDir, "level2_" + i);
          level2Dir.mkdir();
          for (int j = 0; j < count; j++) {
            final File level3Dir = new File(level2Dir, "level3_" + i + "" + j);
            level3Dir.mkdir();
            final File file = new File(level3Dir, "file.txt");
            file.createNewFile();
          }
          final File file = new File(level2Dir, "file.txt");
          file.createNewFile();
        }
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        FileRemover remover = new FileRemover(DirectoryCleanerCallback.EMPTY);
        final File level2Dir = new File(baseDir, "level2_1");
        remover.doDelete(level2Dir, false, true);
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        final String[] dirNames = baseDir.list();
        final File removedDir = new File(baseDir, "level2_1");
        final File removedDirFile = new File(removedDir, "file.txt");
        assertFalse(removedDir.exists());
        assertContains(Arrays.asList(dirNames), "level2_0", "level2_2");
        final String[] lines = myBuildLog.toString().split("\\n");
        boolean removedDirLogged = false;
        for (String line : lines) {
          assertNotContains(line, String.format("deleted %s", removedDirFile.getAbsolutePath()), false);
          if (line.endsWith("deleted " + removedDir.getAbsolutePath())){
            assertFalse(removedDirLogged);
            removedDirLogged = true;
          }
        }
        assertTrue(removedDirLogged);
        checkResults(17, 0, 0, 1);
      }
    });
  }

  public void should_process_deleted_last_dir_as_one_entry() throws Exception {
    final int count=3;
    final File baseDir = new File(myCheckoutDir, "baseDir");

    doTest(new ActionThrow<Exception>() {
      public void apply() throws Exception {
        baseDir.mkdir();
        for (int i = 0; i < count; i++) {
          final File level2Dir = new File(baseDir, "level2_" + i);
          level2Dir.mkdir();
          for (int j = 0; j < count; j++) {
            final File level3Dir = new File(level2Dir, "level3_" + i + "" + j);
            level3Dir.mkdir();
            final File file = new File(level3Dir, "file.txt");
            file.createNewFile();
          }
          final File file = new File(level2Dir, "file.txt");
          file.createNewFile();
        }
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        FileRemover remover = new FileRemover(DirectoryCleanerCallback.EMPTY);
        final File level2Dir = new File(baseDir, "level2_2");
        remover.doDelete(level2Dir, false, true);
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        final String[] dirNames = baseDir.list();
        final File removedDir = new File(baseDir, "level2_2");
        final File removedDirFile = new File(removedDir, "file.txt");
        assertFalse(removedDir.exists());
        assertContains(Arrays.asList(dirNames), "level2_0", "level2_1");
        final String[] lines = myBuildLog.toString().split("\\n");
        boolean removedDirLogged = false;
        for (String line : lines) {
          assertNotContains(line, String.format("deleted %s", removedDirFile.getAbsolutePath()), false);
          if (line.endsWith("deleted " + removedDir.getAbsolutePath())){
            assertFalse(removedDirLogged);
            removedDirLogged = true;
          }
        }
        assertTrue(removedDirLogged);
        checkResults(17, 0, 0, 1);
      }
    });
  }

  public void folder_became_file() throws Exception {
    final File baseDir = new File(myCheckoutDir, "baseDir");

    doTest(new ActionThrow<Exception>() {
      public void apply() throws Exception {
        baseDir.mkdir();
        for (int i = 0; i < 3; i++) {
          final File level2Dir = new File(baseDir, "level2_" + i);
          level2Dir.mkdir();
          for (int j = 0; j < 3; j++) {
            final File level3Dir = new File(level2Dir, "level3_" + i + "" + j);
            level3Dir.mkdir();
            final File file = new File(level3Dir, "file.txt");
            file.createNewFile();
          }
          final File file = new File(level2Dir, "file.txt");
          file.createNewFile();
        }
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        FileRemover remover = new FileRemover(DirectoryCleanerCallback.EMPTY);
        final File level2Dir = new File(baseDir, "level2_1");
        remover.doDelete(level2Dir, false, true);
        assertTrue(level2Dir.createNewFile());
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        final String[] dirNames = baseDir.list();
        final File removedDir = new File(baseDir, "level2_1");
        final File removedDirFile = new File(removedDir, "file.txt");
        assertFalse(removedDir.exists());
        assertContains(Arrays.asList(dirNames), "level2_0", "level2_2");
        final String[] lines = myBuildLog.toString().split("\\n");
        boolean removedDirLogged = false;
        boolean addedFileLogged = false;
        for (String line : lines) {
          assertNotContains(line, String.format("deleted %s", removedDirFile.getAbsolutePath()), false);
          if (line.endsWith("new and deleted " + removedDir.getAbsolutePath())){
            assertFalse(removedDirLogged);
            addedFileLogged = true;
          } else if (line.endsWith("deleted " + removedDir.getAbsolutePath())) {
            assertFalse(removedDirLogged);
            removedDirLogged = true;
          }
        }
        assertTrue(removedDirLogged);
        assertTrue(addedFileLogged);
        checkResults(17, 0, 1,1);
      }
    });
  }

  public void modified_file() throws Exception {
    final File baseDir = new File(myCheckoutDir, "baseDir");
    final File file = new File(baseDir, "file.txt");
    doTest(new ActionThrow<Exception>() {
      public void apply() throws Exception {
        baseDir.mkdir();
        file.createNewFile();
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        FileUtil.writeFile(file, "testData", "utf-8");
      }
    }, new ActionThrow<Exception>() {
      public void apply() throws Exception {
        final String[] lines = myBuildLog.toString().split("\\n");
        boolean modifiedFileLogged = false;
        for (String line : lines) {
          if (line.endsWith("modified " + file.getAbsolutePath())){
            modifiedFileLogged = true;
          }
        }
        assertTrue(modifiedFileLogged);
        checkResults(1, 1, 0, 0);
      }
    });
  }

  private void checkResults(int unchanged, int modified, int added, int deleted){
    if (added == 0) {
      assertContains(myBuildLog.toString(),
                     String.format("Detected %d unchanged, %d newly created, %d modified, %d deleted files and directories", unchanged, added, modified, deleted));
    } else {
      assertContains(myBuildLog.toString(),
                     String.format("Detected %d unchanged, %d newly created (%d of them deleted), %d modified, %d deleted files and directories", unchanged, added, added, modified, deleted));
    }
  }

  @AfterMethod
  public void tearDown() throws Exception {
    System.out.println(myBuildLog.toString());
    super.tearDown();
  }

}
