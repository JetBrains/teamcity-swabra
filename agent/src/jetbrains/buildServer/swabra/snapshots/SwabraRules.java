/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.io.File;
import java.util.*;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.FileRule;
import jetbrains.buildServer.vcs.FileRuleSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: vbedrosova
 * Date: 02.12.10
 * Time: 15:16
 */
public class SwabraRules {
  @NotNull
  private final File myBaseDir;
  @NotNull
  private final RuleSet myRuleSet;
  private List<File> myRootPaths;

  public SwabraRules(@NotNull File baseDir, @NotNull Collection<String> body) {
    myBaseDir = baseDir;

    final ArrayList<String> rules = new ArrayList<String>(body);
    addAsRule(myBaseDir, rules);

    myRuleSet = new RuleSet(rules);
  }

  private static void addAsRule(@NotNull File baseDir, @NotNull Collection<String> body) {
    body.add(baseDir.getAbsolutePath());
  }

  @NotNull
  private String resolvePath(@NotNull String path, @NotNull File baseDir) {
    if (StringUtil.isEmptyOrSpaces(path)) return path;

    final File file = new File(path);
    return file.isAbsolute() ? file.getAbsolutePath() : baseDir.getAbsolutePath() + "/" + path;
  }

  public List<File> getPaths() {
    return myRootPaths;
  }

  public boolean shouldInclude(@NotNull String path) {
    return myRuleSet.shouldInclude(path);
  }

  public List<String> getRulesForPath(@NotNull File pathFile) {
    return myRuleSet.getForPath(pathFile.getPath());
  }

  private final class RuleSet extends FileRuleSet<FileRule, FileRule> {
    public RuleSet(List<String> body) {
      super(body);
    }

    @Override
    protected void doPostInitProcess(final List<FileRule> includeRules, final List<FileRule> excludeRules) {
      sortByFrom(includeRules, false);
      sortByFrom(excludeRules, true);
      initRootIncludePaths();
    }

    @Override
    protected FileRule createNewIncludeRule(final String line) {
      return createRule(line, true);
    }

    @Override
    protected FileRule createNewExcludeRule(final String line) {
      return createRule(line, false);
    }

    @Override
    protected FileRule createNewIncludeRule(final FileRule includeRule) {
      return createNewIncludeRule(includeRule.getFrom());
    }

    @Override
    protected FileRule createNewExcludeRule(final FileRule excludeRule) {
      return createNewExcludeRule(excludeRule.getFrom());
    }

    private FileRule createRule(@NotNull String line, boolean isInclude) {
      return new FileRule(resolvePath(line, myBaseDir), null, this, isInclude);
    }

    private void initRootIncludePaths() {
      final ArrayList<FileRule> resultRules = new ArrayList<FileRule>();
      resultRules.addAll(getIncludeRules());

      final Set<FileRule> processedRules = new HashSet<FileRule>();

      for (Iterator<FileRule> iterator = resultRules.iterator(); iterator.hasNext();) {
        FileRule rule = iterator.next();
        boolean add = true;

        if (!shouldInclude(rule.getFrom())) {
          add = false;
        }

        for (FileRule processed : processedRules) {
          if (processed.getMatchedHead(rule.getFrom()) != null) {
            add = false;
            break;
          }
        }

        if (add) processedRules.add(rule);
        else iterator.remove();
      }

      final Set<File> rootPaths = new HashSet<File>();
      for (FileRule rule : resultRules) {
        rootPaths.add(new File(getPathWithoutWildcards(rule.getFrom())));
      }
      myRootPaths = new ArrayList<File>(rootPaths);
    }

    public List<String> getForPath(@NotNull String path) {
      path = preparePath(path);

      final ArrayList<String> rules = new ArrayList<String>();
      for (FileRule rule : getAllRulesSorted()) {
        if (isSubDir(rule.getFrom(), path)) {
          rules.add(rule.toString());
        }
      }
      return rules;
    }

    private List<FileRule> getAllRulesSorted() {
      final ArrayList<FileRule> allRules = new ArrayList<FileRule>(getIncludeRules().size() + getExcludeRules().size());
      allRules.addAll(getIncludeRules());
      allRules.addAll(getExcludeRules());
      sortByFrom(allRules, false);
      return allRules;
    }

    @NotNull
    @Override
    public String preparePath(@Nullable final String path) {
      if (StringUtil.isEmptyOrSpaces(path)) {
        return StringUtil.EMPTY;
      }
      return new File(path).isAbsolute() ? FileUtil.normalizeAbsolutePath(FileUtil.normalizeSeparator(path)).replace("\\", "/") : FileUtil.normalizeRelativePath(path);
    }
  }

  @NotNull
  private String getPathWithoutWildcards(@NotNull String from) {
    return from.contains("*") ?  from.substring(0, from.indexOf('*')): from;
  }
}
