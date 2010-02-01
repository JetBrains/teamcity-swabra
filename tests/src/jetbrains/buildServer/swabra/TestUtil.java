package jetbrains.buildServer.swabra;

import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * User: vbedrosova
 * Date: 30.01.2010
 * Time: 17:43:40
 */
public class TestUtil {
  private static final String TEST_DATA_PATH = "tests" + File.separator + "testData";
  
  public static String getTestDataPath(final String fileName, final String folderName) throws Exception {
    return getTestData(fileName, folderName).getAbsolutePath();
  }

  public static File getTestData(final String fileName, final String folderName) throws Exception {
    final String relativeFileName = TEST_DATA_PATH + (folderName != null ? File.separator + folderName : "") + (fileName != null ? File.separator + fileName : "");
    final File file1 = new File(relativeFileName);
    if (file1.exists()) {
      return file1;
    }
    final File file2 = new File("svnrepo" + File.separator + "swabra" + File.separator + relativeFileName);
    if (file2.exists()) {
      return file2;
    }
    throw new FileNotFoundException("Either " + file1.getAbsolutePath() + " or file " + file2.getAbsolutePath() + " should exist.");
  }

   public static String readFile(@NotNull final File file) throws IOException {
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

}
