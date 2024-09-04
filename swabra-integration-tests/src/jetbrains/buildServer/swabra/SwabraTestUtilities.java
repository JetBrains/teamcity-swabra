package jetbrains.buildServer.swabra;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.BundledTool;
import jetbrains.buildServer.agent.BundledToolsRegistry;
import jetbrains.buildServer.agent.impl.BaseAgentSpringTestCase;
import jetbrains.buildServer.agent.impl.directories.*;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.SystemTimeService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.testng.Assert.fail;

/**
 * @author Andrey Shcheglov &lt;mailto:andrey.shcheglov@jetbrains.com&gt;
 */
@SuppressWarnings({
  "AbstractClassNeverImplemented",
  "AbstractClassWithoutAbstractMethods",
  "ClassWithOnlyPrivateConstructors"
})
abstract class SwabraTestUtilities {
  private SwabraTestUtilities() {
    assert false;
  }

  static void setUpSwabra(@NotNull final BaseAgentSpringTestCase test,
                   @NotNull final SBuildType buildType,
                   @NotNull final Callable<BuildProgressLogger> progressLogger) {
    final EventDispatcher<AgentLifeCycleListener> dispatcher = test.getAgentEvents();

    final SwabraLogger logger = createSwabraLogger(progressLogger);

    final DirectoryMapPersistance persistence = new DirectoryMapPersistanceImpl(
      test.getBuildAgentConfiguration(),
      new SystemTimeService());

    final SwabraPropertiesProcessor propertiesProcessor = new SwabraPropertiesProcessor(
      dispatcher,
      logger,
      persistence);

    final DirectoryCleaner directoryCleaner = new DirectoryCleaner() {
      @Override
      public boolean delete(@NotNull final File f, @NotNull final DirectoryCleanerCallback callback) {
        final String message = format("delete(%s, %s)", f, callback);
        try {
          progressLogger.call().message(message);
        } catch (final Exception e) {
          fail(format("%s: %s", message, e.getMessage()), e);
        }

        /*
         * Never called anyway.
         */
        fail(message);

        return false;
      }

      @Override
      public boolean deleteNow(@NotNull final File f, @NotNull final DirectoryCleanerCallback callback) {
        final String message = format("deleteNow(%s, %s)", f, callback);
        try {
          progressLogger.call().message(message);
        } catch (final Exception e) {
          fail(format("%s: %s", message, e.getMessage()), e);
        }

        /*
         * Never called anyway.
         */
        fail(message);

        return false;
      }
    };

    final DirectoryMapDirtyTracker dirtyTracker = new DirectoryMapDirtyTrackerImpl();

    final DirectoryMapDirectoriesCleaner mapDirectoriesCleaner = new DirectoryMapDirectoriesCleanerImpl(
      dispatcher,
      directoryCleaner,
      persistence,
      dirtyTracker);

    /*
     * This already registers the Swabra listener, so @SpringContextFixture is
     * not needed.
     */
    final int expectedListenerCount = dispatcher.getListeners().size() + 1;
    new Swabra(dispatcher,
               logger,
               propertiesProcessor,
               createBundledToolsRegistry(),
               mapDirectoriesCleaner);
    assertThat(dispatcher.getListeners().size(), is(expectedListenerCount));

    /*
     * Build-specific Swabra settings seem to have no effect here,
     * as Swabra added via @SpringContextFixture "resets" (i. e. cleans)
     * the whole checkout directory because of its unknown (clean/dirty) state.
     */
    final Map<String, String> params = new HashMap<String, String>();
    params.put(SwabraUtil.ENABLED, SwabraUtil.AFTER_BUILD);
    params.put(SwabraUtil.VERBOSE, SwabraUtil.TRUE);
    params.put(SwabraUtil.RULES, "+:**/*");
    buildType.addBuildFeature(SwabraUtil.FEATURE_TYPE, params);
  }

  @NotNull
  private static BundledToolsRegistry createBundledToolsRegistry() {
    return new BundledToolsRegistry() {
      @Nullable
      @Override
      public BundledTool findTool(@NotNull final String name) {
        /*
         * Never called anyway.
         */
        fail(format("BundledToolsRegistry.findTool(%s)", name));

        return null;
      }

      @Override
      public void registerTool(@NotNull final String toolName, @NotNull final BundledTool tool) {

      }

      @Override
      public void unregisterTool(@NotNull String toolId) {

      }
    };
  }

  @NotNull
  private static SwabraLogger createSwabraLogger(@NotNull final Callable<BuildProgressLogger> progressLogger) {
    final SwabraLogger logger = new SwabraLogger();

    /*
     * We can't ask for a progress logger until the build starts,
     * hence the trick with a proxy.
     */
    final InvocationHandler invocationHandler = new InvocationHandler() {
      @Override
      public Object invoke(final Object proxy, final Method method, final Object args[]) throws Throwable {
        final Object invocationResult = method.invoke(progressLogger.call());

        /*
         * Never called anyway.
         */
        fail(format("%s() invoked by the Swabra logger", method.getName()));

        return invocationResult;
      }
    };
    final BuildProgressLogger delegatingProgressLogger = (BuildProgressLogger) Proxy.newProxyInstance(
      SwabraTestUtilities.class.getClassLoader(),
      new Class<?>[] {BuildProgressLogger.class},
      invocationHandler);

    logger.setBuildLogger(delegatingProgressLogger);
    return logger;
  }
}
