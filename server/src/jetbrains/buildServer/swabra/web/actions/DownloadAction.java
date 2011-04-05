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

package jetbrains.buildServer.swabra.web.actions;

import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.swabra.HandleProvider;
import jetbrains.buildServer.swabra.web.HandleForm;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 25.05.2010
 * Time: 14:05:01
 */
public class DownloadAction extends BaseAction {
  public DownloadAction(@NotNull HandleProvider handleProvider) {
    super(handleProvider);
  }

  @Override
  public String getType() {
    return "DOWNLOAD";
  }

  @Override
  public void validate(HandleForm form, ActionErrors errors) {
    final String url = form.getUrl();
    if (StringUtil.isEmptyOrSpaces(url)) {
      errors.addError("wrongUrl", "Url is empty");
      return;
    }
    if (!url.startsWith("http://")) {
      errors.addError("wrongUrl", "Url must start with http://");
      return;
    }
    if (!url.endsWith("/handle.exe")) {
      errors.addError("wrongUrl", "Url must end with /handle.exe");
    }
  }

  @Override
  public void apply(HandleForm form) {
    form.setRunning(true);
    form.addMessage("Start downloading SysInternals handle.exe from " + form.getUrl() + "...", Status.NORMAL);
    try {
      myHandleProvider.downloadHandleAndPackPlugin(form.getUrl());
      form.addMessage("Successfully downloaded handle.exe", Status.NORMAL);
      form.addMessage("Created agent plugin at " + myHandleProvider.getPluginFolder(), Status.NORMAL);
      form.addMessage("handle.exe will be present on agents after the upgrade process (will start automatically)", Status.NORMAL);
    } catch (Throwable throwable) {
      form.addMessage("Failed to download handle.exe, please see teamcity-server.log for details", Status.ERROR);
    }
    form.setRunning(false);
  }
}
