/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
  private final SwabraRuleSet mySwabraRuleSet;
  private List<File> myRootPaths;

  public SwabraRules(@NotNull File baseDir, @NotNull Collection<String> body) {
    myBaseDir = baseDir;

    final ArrayList<String> rules = new ArrayList<String>(body);
    addAsRule(myBaseDir, rules);

    mySwabraRuleSet = new SwabraRuleSet(rules);
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

  public boolean requiresListingForDir(File dir){
    List<SwabraFileRule> rulesForPath = mySwabraRuleSet.getRulesForPath(dir.getPath());
    for (SwabraFileRule rule : rulesForPath) {
      if (rule.isRequiresFullListing())
        return true;
    }
    return false;
  }

  public boolean shouldInclude(@NotNull String path) {
    return mySwabraRuleSet.shouldInclude(path);
  }

  public List<String> getRulesForPath(@NotNull File pathFile) {
    return mySwabraRuleSet.getForPath(pathFile.getPath());
  }

  private final class SwabraRuleSet extends FileRuleSet<SwabraFileRule, SwabraFileRule> {
    public SwabraRuleSet(List<String> lines) {
      super(lines);
    }

    @Override
    protected void doPostInitProcess(@NotNull final List<SwabraFileRule> includeRules, final List<SwabraFileRule> excludeRules) {
      sortByFrom(includeRules, false);
      sortByFrom(excludeRules, true);
      initRootIncludePaths();
    }

    @Override
    protected SwabraFileRule createNewIncludeRule(final String line) {
      return createRule(line, true);
    }

    @Override
    protected SwabraFileRule createNewExcludeRule(final String line) {
      return createRule(line, false);
    }

    @Override
    protected SwabraFileRule createNewIncludeRule(final SwabraFileRule includeRule) {
      return createNewIncludeRule(includeRule.getFrom());
    }

    @Override
    protected SwabraFileRule createNewExcludeRule(final SwabraFileRule excludeRule) {
      return createNewExcludeRule(excludeRule.getFrom());
    }

    private SwabraFileRule createRule(@NotNull String line, boolean isInclude) {
      return new SwabraFileRule(resolvePath(line, myBaseDir), null, this, isInclude);
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
        rootPaths.add(getPathWithoutWildcards(new File(rule.getFrom())));
      }
      myRootPaths = new ArrayList<File>(rootPaths);
    }

    public List<String> getForPath(@NotNull String path) {
      path = preparePath(path);

      final ArrayList<String> rules = new ArrayList<String>();
      for (SwabraFileRule rule : getAllRulesSorted()) {
        if (isSubDir(rule.getFrom(), path)) {
          rules.add(rule.toString());
        }
      }
      return rules;
    }

    public List<SwabraFileRule> getRulesForPath(@NotNull String path) {
      path = preparePath(path);

      final ArrayList<SwabraFileRule> rules = new ArrayList<SwabraFileRule>();
      for (SwabraFileRule rule : getAllRulesSorted()) {
        if (isSubDir(rule.getFrom(), path)) {
          rules.add(rule);
        }
      }
      return rules;
    }

    private List<SwabraFileRule> getAllRulesSorted() {
      final ArrayList<SwabraFileRule> allRules = new ArrayList<SwabraFileRule>(getIncludeRules().size() + getExcludeRules().size());
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

  private static class SwabraFileRule extends FileRule<SwabraRuleSet>{
    private final boolean requiresFullListing;

    private SwabraFileRule(@NotNull final String fromPath, @Nullable final String additionalProperties, final SwabraRuleSet swabraRuleSet, final boolean isInclude) {
      super(fromPath, additionalProperties, swabraRuleSet, isInclude);
      if (!fromPath.contains("*")){
        requiresFullListing = false;
      } else {
        File ruleParent = new File(fromPath).getParentFile();
        requiresFullListing = ruleParent != null && ruleParent.getPath().contains("*");
      }
    }

    public boolean isRequiresFullListing() {
      return requiresFullListing;
    }
  }

  @NotNull
  private File getPathWithoutWildcards(@NotNull File from) {
    while(from != null && from.getPath().contains("*")){
      from = from.getParentFile();
    }
    return from == null ? new File("") : from;
  }
}
