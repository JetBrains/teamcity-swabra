package jetbrains.buildServer.swabra.processes;

/**
 * User: vbedrosova
 * Date: 04.05.2010
 * Time: 20:55:13
 */
public class GetPidsException extends Exception {
  public GetPidsException(String message, Throwable cause) {
    super(message, cause);
  }

  public GetPidsException(String message) {
    super(message);
  }
}
