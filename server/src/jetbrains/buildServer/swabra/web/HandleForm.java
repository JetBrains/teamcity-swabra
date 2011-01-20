/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package jetbrains.buildServer.swabra.web;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.web.util.CameFromSupport;
import jetbrains.buildServer.web.util.WebUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * User: vbedrosova
 * Date: 26.02.2010
 * Time: 13:12:22
 */
public class HandleForm {
  private String myUrl = "http://live.sysinternals.com/handle.exe";
  private String myHandleFile;
  private String myLoadType = "UPLOAD";
  private boolean myRunning = false;
  private final List<String> myLoadHandleMessages = new ArrayList<String>();
  private final CameFromSupport myCameFromSupport = new CameFromSupport();

  private int myErrors = 0;
  private final SimpleDateFormat myTimestampFormat = new SimpleDateFormat("HH:mm:ss");

  public String getUrl() {
    return myUrl;
  }

  public void setUrl(String url) {
    myUrl = url;
  }

  public String getHandleFile() {
    return myHandleFile;
  }

  public void setHandleFile(String handleFile) {
    myHandleFile = handleFile;
  }

  public String getLoadType() {
    return myLoadType;
  }

  public void setLoadType(String loadType) {
    myLoadType = loadType;
  }

  public boolean isRunning() {
    return myRunning;
  }

  public void setRunning(boolean running) {
    myRunning = running;
  }

  public List<String> getLoadHandleMessages() {
    return myLoadHandleMessages;
  }

  public void clearMessages() {
    myLoadHandleMessages.clear();
    myErrors = 0;
  }

  public CameFromSupport getCameFromSupport() {
    return myCameFromSupport;
  }

  public void addMessage(String text, Status status) {
    boolean error = status.above(Status.WARNING);
    if (error) {
      myErrors++;
    }
    String errorId = error ? "id='errorNum:" + myErrors + "'" : "";
    final String escapedText = WebUtil.escapeXml(text).replace("\n", "<br/>");
    String className = "";
    if (error) {
      className = "errorMessage";
    }

    String finalText = escapedText;
    if (error) {
      finalText = "<span " + errorId + " class='" + className + "'>" + escapedText + "</span>";
    }

    myLoadHandleMessages.add(timestamp() + finalText);
  }

  private String timestamp() {
    return "[" + myTimestampFormat.format(Dates.now()) + "]: ";
  }
}
