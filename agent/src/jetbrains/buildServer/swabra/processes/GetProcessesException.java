

package jetbrains.buildServer.swabra.processes;

/**
 * User: vbedrosova
 * Date: 04.05.2010
 * Time: 20:55:13
 */
public class GetProcessesException extends Exception {
  public GetProcessesException(String message, Throwable cause) {
    super(message, cause);
  }

  public GetProcessesException(String message) {
    super(message);
  }
}