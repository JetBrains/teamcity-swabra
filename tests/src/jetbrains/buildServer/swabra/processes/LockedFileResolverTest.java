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

package jetbrains.buildServer.swabra.processes;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.Function;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.agent.ClasspathUtil;
import jetbrains.buildServer.processes.ProcessFilter;
import jetbrains.buildServer.processes.ProcessNode;
import jetbrains.buildServer.processes.ProcessTreeTerminator;
import jetbrains.buildServer.util.ExceptionUtil;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.WaitFor;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

public class LockedFileResolverTest {
  private LockedFileResolver.LockingProcessesProvider myLockingProcessesProvider;
  private ResolverListener myListener;
  private List<String> myIgnoredProcesses;
  private TempFiles myTempFiles;
  private Mockery myMockery;

  @Before
  public void setUp() throws Exception {
    myTempFiles = new TempFiles();

    myMockery = new Mockery();

    myLockingProcessesProvider = myMockery.mock(LockedFileResolver.LockingProcessesProvider.class);
    myIgnoredProcesses = new ArrayList<String>();
    myListener = new ResolverListener();
  }

  @After
  public void tearDown() throws Exception {
    myMockery.assertIsSatisfied();
    myTempFiles.cleanup();
  }

  @NotNull
  private LockedFileResolver getLockedFileResolver() {
    return new LockedFileResolver(myLockingProcessesProvider, myIgnoredProcesses, new WmicProcessDetailsProvider());
  }

  @Test
  public void test_no_locking_processes() throws IOException, GetProcessesException {
    File tempDir = myTempFiles.createTempDir();

    setupLockingProcesses(Collections.<ProcessInfo>emptyList(), tempDir);
    getLockedFileResolver().resolve(tempDir, false, myListener);
    myListener.assertMessageContains("No processes found locking files in directory");
  }

  @Test
  public void test_locking_process_ignored() throws IOException, GetProcessesException {
    File tempDir = myTempFiles.createTempDir();

    setupLockingProcesses(Collections.singletonList(new ProcessInfo(25L, "java.exe")), tempDir);
    setupIgnoredProcesses(Collections.singletonList("java.exe"));

    getLockedFileResolver().resolve(tempDir, true, myListener);
    myListener.assertWarnContains("The following process and it's subtree has been skipped to avoid stopping of TeamCity agent:\nPID: 25\njava.exe");
    myListener.assertWarnDoesNotContain("Failed to kill locking process for");
  }

  @Test
  public void test_locking_process_killed() throws Throwable {
    if (!SystemInfo.isWindows) return;

    final File tempDir = myTempFiles.createTempDir();

    final ExecResult[] res = new ExecResult[1];
    Thread t = new Thread(new Runnable() {
      public void run() {
        try {
          res[0] = startProcess(tempDir.getAbsolutePath(), tempDir);
        } catch (Throwable throwable) {
          ExceptionUtil.rethrowAsRuntimeException(throwable);
        }
      }
    });
    t.start();

    new WaitFor() {
      @Override
      protected boolean condition() {
        return !ProcessTreeTerminator.getChildProcesses(ProcessFilter.MATCH_ALL).isEmpty();
      }
    };

    Assert.assertNull("Process did not start: " + res[0], res[0]);

    Collection<ProcessNode> children = ProcessTreeTerminator.getChildProcesses(ProcessFilter.MATCH_ALL);
    Assert.assertFalse(children.isEmpty());

    ProcessNode child = children.iterator().next();

    setupLockingProcesses(Collections.singletonList(new ProcessInfo(child.getPid(), child.getCommandLine())), tempDir);
    getLockedFileResolver().resolve(tempDir, true, myListener);

    t.join();

    myListener.assertWarnContains("Found process locking files in directory");
    myListener.assertMessageContains("Process killed:\nPID: " + child.getPid());
  }

  // used from test
  public static void main(String[] args) throws IOException {
    File dir = new File(args[0]);
    FileOutputStream fos = new FileOutputStream(new File(dir, "file.txt"));
    fos.write(1);
    ThreadUtil.sleep(3600*1000);
  }


  private void setupLockingProcesses(@NotNull final List<ProcessInfo> lockingProcesses, @NotNull final File dir) throws GetProcessesException {
    myMockery.checking(new Expectations() {{
      allowing(myLockingProcessesProvider).getLockingProcesses(dir);
      will(returnValue(lockingProcesses));
    }});
  }

  private void setupIgnoredProcesses(@NotNull List<String> processes) {
    myIgnoredProcesses.addAll(processes);
  }


  private static class ResolverListener implements LockedFileResolver.Listener {
    private final List<String> myMessages = new ArrayList<String>();
    private final List<String> myWarns = new ArrayList<String>();

    public void message(final String m) {
      myMessages.add(m);
    }

    public void warning(final String w) {
      myWarns.add(w);
    }

    @NotNull
    public List<String> getMessages() {
      return myMessages;
    }

    @NotNull
    public List<String> getWarns() {
      return myWarns;
    }

    public void assertMessageContains(@NotNull String part) {
      for (String msg: myMessages) {
        if (msg.contains(part)) return;
      }
      Assert.fail("Messages do not contain \"" + part + "\", messages: " + myMessages);
    }

    public void assertWarnContains(@NotNull String part) {
      for (String msg: myWarns) {
        if (msg.contains(part)) return;
      }
      Assert.fail("Warnings do not contain \"" + part + "\", warnings: " + myWarns);
    }

    public void assertWarnDoesNotContain(@NotNull String part) {
      for (String msg: myWarns) {
        if (msg.contains(part)) {
          Assert.fail("Warning must not contain \"" + part + "\", warning: " + msg);
        }
      }
    }
  }

  @NotNull
  public ExecResult startProcess(@NotNull final String argLine, @NotNull File workingDir) throws Throwable {
    final GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setExePath(System.getProperty("java.home") + "/bin/java");
    cmd.addParameter("-classpath");
    cmd.addParameter(getCurrentClasspath());
    cmd.addParameter("-ea");
    cmd.addParameter(getClass().getName());
    cmd.addParameter(argLine);
    cmd.setWorkingDirectory(workingDir);

    return SimpleCommandLineProcessRunner.runCommand(cmd, null);
  }

  public String getCurrentClasspath() throws URISyntaxException, IOException {
    List<Class> refernceClasses = Arrays.<Class>asList(
      BaseTestCase.class,
      getClass(),
      Assert.class,
      Matcher.class,
      TempFiles.class,
      Function.class,
      LockedFileResolver.class,
      ThreadUtil.class
    );

    final String sep = System.getProperty("path.separator");
    StringBuilder sb = new StringBuilder();
    boolean isFirst = true;
    for (Class<?> s : refernceClasses) {
      if (!isFirst) {
        sb.append(sep);
      } else {
        isFirst = false;
      }
      sb.append(ClasspathUtil.getClasspathEntry(s));
    }

    return sb.toString();
  }
}
