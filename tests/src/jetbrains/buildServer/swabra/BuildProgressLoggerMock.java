

package jetbrains.buildServer.swabra;

import java.util.Date;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.agent.FlowLogger;
import jetbrains.buildServer.messages.BuildMessage1;
import jetbrains.buildServer.messages.Status;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 21.04.2009
 * Time: 15:03:19
 */
public class BuildProgressLoggerMock implements FlowLogger {
  private final StringBuilder myText;

  public BuildProgressLoggerMock(final StringBuilder text) {
    myText = text;
  }

  public void warning(@NotNull final String message) {
    myText.append("WARNING: ");
    myText.append(message);
    myText.append("\n");
  }

  public void exception(final Throwable th) {
    myText.append("EXCEPTION: ");
    myText.append(th.toString());
    myText.append("\n");
  }

  public void progressMessage(final String message) {
    myText.append("PROGRESS: ");
    myText.append(message);
    myText.append("\n");
  }

  public void message(final String message) {
    myText.append("MESSAGE: ");
    myText.append(message);
    myText.append("\n");
  }

  @Override
  public void debug(final String message) {
    myText.append("DEBUG: ");
    myText.append(message);
    myText.append("\n");
  }

  @Override
  public void message(final String message, final Status status) {
    message(message);
  }

  public void error(@NotNull final String message) {
    myText.append("ERROR: ");
    myText.append(message);
    myText.append("\n");
  }

  public void activityStarted(final String activityName, final String activityType) {
    activityStarted(activityName, null, activityType);
  }

  public void activityStarted(final String activityName, final String activityDescription, final String activityType) {
    myText.append("ACTIVITY STARTED: ");
    myText.append(activityName);
    myText.append("\n");
  }

  public void activityFinished(final String activityName, final String activityType) {
    myText.append("ACTIVITY FINISHED: ");
    myText.append(activityName);
    myText.append("\n");
  }

  public void targetStarted(final String targetName) {
    myText.append("TARGET STARTED: ");
    myText.append(targetName);
    myText.append("\n");
  }

  public void targetFinished(final String targetName) {
    myText.append("TARGET FINISHED: ");
    myText.append(targetName);
    myText.append("\n");
  }

  public void buildFailureDescription(final String message) {
  }

  public void internalError(final String type, final String message, final Throwable throwable) {
  }

  public void progressStarted(final String message) {
  }

  public void progressFinished() {
  }

  public void logMessage(final BuildMessage1 message) {
  }

  public void logTestStarted(final String name) {
  }

  public void logTestStarted(final String name, final Date timestamp) {
  }

  public void logTestFinished(final String name) {
  }

  public void logTestFinished(final String name, final Date timestamp) {
  }

  public void logTestIgnored(final String name, final String reason) {
  }

  public void logSuiteStarted(final String name) {
  }

  public void logSuiteStarted(final String name, final Date timestamp) {
  }

  public void logSuiteFinished(final String name) {
  }

  public void logSuiteFinished(final String name, final Date timestamp) {
  }

  public void logTestStdOut(final String testName, final String out) {
  }

  public void logTestStdErr(final String testName, final String out) {
  }

  public void logTestFailed(final String testName, final Throwable e) {
  }

  public void logComparisonFailure(final String testName, final Throwable e, final String expected, final String actual) {
  }

  public void logTestFailed(final String testName, final String message, final String stackTrace) {
  }

  public void flush() {
  }

  public void ignoreServiceMessages(final Runnable runnable) {
    runnable.run();
  }

  public FlowLogger getFlowLogger(final String flowId) {
    return this;
  }

  public FlowLogger getThreadLogger() {
    return this;
  }

  public void startFlow() {
  }

  public String getFlowId() {
    return "test-flow-id";
  }

  public void disposeFlow() {
  }

  public void logBuildProblem(final BuildProblemData buildProblem) {
  }
}