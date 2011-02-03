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
