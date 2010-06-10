package jetbrains.buildServer.swabra.web.actions;

import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.swabra.HandleProvider;
import jetbrains.buildServer.swabra.web.HandleForm;
import org.jetbrains.annotations.NotNull;

/**
 * User: vbedrosova
 * Date: 25.05.2010
 * Time: 14:00:25
 */
public abstract class BaseAction {
  @NotNull
  protected final HandleProvider myHandleProvider;

  protected BaseAction(@NotNull HandleProvider handleProvider) {
    myHandleProvider = handleProvider;
  }

  public abstract String getType();

  public void validate(HandleForm form, ActionErrors errors) {
  }

  public abstract void apply(HandleForm form);
}
