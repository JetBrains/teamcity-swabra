package jetbrains.buildServer.swabra;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.agent.plugins.beans.PluginDescriptor;
import jetbrains.buildServer.handleProvider.HandleProvider;
import jetbrains.buildServer.swabra.processes.HandlePathProvider;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import junit.framework.TestCase;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 *         Date: 20.04.11 16:33
 */
public class HandleProviderTest extends TestCase {
  private final String EXPECTED_HANDLE_EXE_PATH = "swabra.handle.exe.path";
  private TempFiles myTempFiles;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTempFiles = new TempFiles();
    myTempFiles.cleanup();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myTempFiles.cleanup();
  }

  @Test
  public void testHandleRegistered_hoHandleOnDisk() throws IOException {
    Mockery m = new Mockery();
    final PluginDescriptor descriptor = m.mock(PluginDescriptor.class);
    final BuildAgentConfiguration config = m.mock(BuildAgentConfiguration.class);
    final BuildAgent agent = m.mock(BuildAgent.class);
    final BuildAgentSystemInfo info = m.mock(BuildAgentSystemInfo.class);

    final EventDispatcher<AgentLifeCycleListener> dispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
    dispatcher.setErrorHandler(new EventDispatcher.ErrorHandler() {
      public void handle(final Throwable e) {
        throw new Error(e);
      }
    });

    final File root = myTempFiles.createTempDir();
    m.checking(new Expectations(){{
      allowing(agent).getConfiguration(); will(returnValue(config));
      allowing(descriptor).getPluginRoot(); will(returnValue(root));
      allowing(config).getSystemInfo(); will(returnValue(info));
      allowing(info).isWindows(); will(returnValue(true));
    }});

    new HandleProvider(dispatcher, config, descriptor);
    dispatcher.getMulticaster().beforeAgentConfigurationLoaded(agent);

    m.assertIsSatisfied();
  }

  @Test
  public void testHandleRegistered_handleFound() throws IOException {
    Mockery m = new Mockery();
    final PluginDescriptor descriptor = m.mock(PluginDescriptor.class);
    final BuildAgentConfiguration config = m.mock(BuildAgentConfiguration.class);
    final BuildAgent agent = m.mock(BuildAgent.class);
    final BuildAgentSystemInfo info = m.mock(BuildAgentSystemInfo.class);

    final EventDispatcher<AgentLifeCycleListener> dispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
    dispatcher.setErrorHandler(new EventDispatcher.ErrorHandler() {
      public void handle(final Throwable e) {
        throw new Error(e);
      }
    });

    final File root = myTempFiles.createTempDir();
    new File(root, "bin").mkdirs();
    final File handleFile = new File(root, "bin/handle.exe");
    FileUtil.writeFile(handleFile, "this seems to be handle.exe. But it not runnable now");
    m.checking(new Expectations(){{
      allowing(agent).getConfiguration(); will(returnValue(config));
      allowing(descriptor).getPluginRoot(); will(returnValue(root));
      allowing(config).getSystemInfo(); will(returnValue(info));
      allowing(info).isWindows(); will(returnValue(true));
      oneOf(config).addConfigurationParameter(EXPECTED_HANDLE_EXE_PATH, handleFile.getPath());
    }});

    new HandleProvider(dispatcher, config, descriptor);
    dispatcher.getMulticaster().beforeAgentConfigurationLoaded(agent);

    m.assertIsSatisfied();
  }

  @Test
  public void testSwabraSettings() throws IOException {
    final Mockery m = new Mockery();
    final AgentRunningBuild build = m.mock(AgentRunningBuild.class);
    final Map<String, String> map = new HashMap<String, String>();

    final File root = myTempFiles.createTempDir();
    new File(root, "bin").mkdirs();
    final File handleFile = new File(root, "bin/handle.exe");
    FileUtil.writeFile(handleFile, "this seems to be handle.exe. But it not runnable now");

    map.put(EXPECTED_HANDLE_EXE_PATH, handleFile.getPath());

    m.checking(new Expectations(){{
      allowing(build).getSharedConfigParameters();will(returnValue(map));
    }});

    Assert.assertNotNull(new HandlePathProvider(new SwabraLogger(), build).getHandlePath());
  }

}
