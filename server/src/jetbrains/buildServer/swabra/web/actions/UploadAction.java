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
      form.addMessage("handle.exe will be present on agents after the upgrade process (will start automatically)", Status.NORMAL);
    } catch (Throwable throwable) {
      form.addMessage("Failed to upload handle.exe, please see teamcity-server.log for details", Status.ERROR);
    }
    form.setRunning(false);
  }
}
