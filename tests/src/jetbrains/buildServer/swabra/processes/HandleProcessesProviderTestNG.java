/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.io.IOException;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import org.jetbrains.annotations.NotNull;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author vbedrosova
 */
@Test
public class HandleProcessesProviderTestNG extends BaseTestCase {
  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (!SystemInfo.isWindows) throw new SkipException("This test is only for Windows");
  }

  @TestFor(issues = "TW-47665")
  public void should_respect_negative_exit_code() throws Throwable {
    try {
      createProvider(createHandleExe("<head><title>Document Moved</title></head>\n" +
                                     "<body><h1>Object Moved</h1>This document may be found <a HREF=\"https://live.sysinternals.com/handle.exe\">here</a></body>")).getLockingProcesses(createTempFile());
    } catch (GetProcessesException e) {
      assertContains(e.getMessage(), "exit code: -1");
      return;
    }
    fail("Exception was expected");
  }

  @TestFor(issues = "TW-47665")
  public void test_empty_handle_exe() throws Throwable {
    try {
      createProvider(createHandleExe("")).getLockingProcesses(createTempFile());
    } catch (GetProcessesException e) {
      assertContains(e.getMessage(), "exit code: -1");
      return;
    }
    fail("Exception was expected");
  }

  @NotNull
  private HandleProcessesProvider createProvider(@NotNull String handleExe) throws IOException {
    return new HandleProcessesProvider(handleExe);
  }

  @NotNull
  private String createHandleExe(@NotNull String content) throws IOException {
    final File handle = new File(createTempDir(), "handle.exe");
    FileUtil.writeFile(handle, content, "UTF-8");
    return handle.getAbsolutePath();
  }
}
