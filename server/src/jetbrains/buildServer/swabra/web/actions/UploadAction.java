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

import java.io.File;

/**
 * User: vbedrosova
 * Date: 25.05.2010
 * Time: 14:04:35
 */
public class UploadAction extends BaseAction {
  public UploadAction(@NotNull HandleProvider handleProvider) {
    super(handleProvider);
  }

  @Override
  public String getType() {
    return "UPLOAD";
  }

  @Override
  public void validate(HandleForm form, ActionErrors errors) {
    final String file = form.getHandleFile();
    if (StringUtil.isEmptyOrSpaces(file)) {
      errors.addError("wrongFile", "File is invalid");
      return;
    }
    if (!file.endsWith("handle.exe")) {
      errors.addError("wrongFile", "File name must be handle.exe");
    }
  }

  @Override
  public void apply(HandleForm form) {
    form.setRunning(true);
    form.addMessage("Start uploading SysInternals handle.exe...", Status.NORMAL);
    try {
      myHandleProvider.packPlugin(new File(form.getHandleFile()));
      form.addMessage("Successfully uploaded handle.exe", Status.NORMAL);
      form.addMessage("Created agent plugin at " + myHandleProvider.getPluginFolder(), Status.NORMAL);
      form.addMessage("handle.exe will be present on agents after the upgrade process (will start automatically)", Status.NORMAL);
    } catch (Throwable throwable) {
      form.addMessage("Failed to upload handle.exe, please see teamcity-server.log for details", Status.ERROR);
    }
    form.setRunning(false);
  }
}
