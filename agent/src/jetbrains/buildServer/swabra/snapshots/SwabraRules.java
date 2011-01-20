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

package jetbrains.buildServer.swabra.snapshots;

import jetbrains.buildServer.vcs.FileRule;
import jetbrains.buildServer.vcs.FileRuleSet;

import java.util.List;

/**
 * User: vbedrosova
 * Date: 02.12.10
 * Time: 15:16
 */
public class SwabraRules extends FileRuleSet<FileRule, FileRule> {
  public SwabraRules(List<String> body) {
    super(body);
  }

  @Override
  protected void doPostInitProcess(List<FileRule> includeRules, List<FileRule> excludeRules) {
  }

  @Override
  protected FileRule createNewIncludeRule(String rule) {
    return new FileRule<SwabraRules>(rule, this, true);
  }

  @Override
  protected FileRule createExcludeRule(String line) {
    return new FileRule<SwabraRules>(line, this, false);
  }

  @Override
  protected FileRule createNewIncludeRule(FileRule includeRule) {
    return createNewIncludeRule(includeRule.getFrom());
  }

  @Override
  protected FileRule createNewExcludeRule(FileRule excludeRule) {
    return createExcludeRule(excludeRule.getFrom());
  }
}
