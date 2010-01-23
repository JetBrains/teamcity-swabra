package jetbrains.buildServer.swabra.snapshots;

import java.text.ParseException;

/**
 * User: vbedrosova
 * Date: 23.01.2010
 * Time: 14:42:00
 */
public class SnapshotUtil {
  public static final String SEPARATOR = "\t";
  public static final String FILE_SUFFIX = ".snapshot";

  public static String encodeDate(long timestamp) {
    return String.valueOf(timestamp);
  }

  public static long decodeDate(String encodedDate) throws ParseException {
    return Long.parseLong(encodedDate);
  }
}
