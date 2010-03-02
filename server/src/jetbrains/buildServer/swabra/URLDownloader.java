/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import org.apache.log4j.Logger;

import java.io.*;
import java.net.URL;

/**
 * User: vbedrosova
 * Date: 29.12.2009
 * Time: 14:56:17
 */
public class URLDownloader {
  private static final Logger LOG = Logger.getLogger(URLDownloader.class);

  private static final int DOWNLOAD_TRY_NUMBER = 10;

  public static void download(URL source, File dest) throws IOException {
    LOG.info(new StringBuilder("Downloading object from ").append(source).
      append(" to ").append(dest.getAbsolutePath()).append("...").toString());

    InputStream inputStream = null;
    OutputStream outputStream = null;
    int threshold = DOWNLOAD_TRY_NUMBER;
    while (threshold > 0) {
      IOException result = null;
      try {
        inputStream = source.openStream();
        outputStream = new FileOutputStream(dest);
        final byte[] buffer = new byte[10 * 1024];
        int count;
        while ((count = inputStream.read(buffer)) > 0) {
          outputStream.write(buffer, 0, count);
        }
      } catch (IOException e) {
        result = e;
      } finally {
        try {
          close(inputStream);
          close(outputStream);
        } catch (IOException e) {
          result = e;
        }
      }
      if (result == null) {
        LOG.info(new StringBuilder("Successfully downloaded object from ").append(source).
          append(" to ").append(dest.getAbsolutePath()).toString());

        return;
      }
      --threshold;
    }

    LOG.error(new StringBuilder("Unable to download object from ").append(source).
      append(" to ").append(dest.getAbsolutePath()).
      append(" from ").append(DOWNLOAD_TRY_NUMBER).append(" tries").toString());
  }

  private static void close(Closeable c) throws IOException {
    if (c != null) {
      c.close();
    }
  }
}