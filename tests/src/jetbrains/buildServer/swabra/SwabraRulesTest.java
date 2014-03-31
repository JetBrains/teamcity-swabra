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

package jetbrains.buildServer.swabra;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import jetbrains.buildServer.swabra.snapshots.SwabraRules;
import jetbrains.buildServer.util.*;
import jetbrains.buildServer.util.filters.Filter;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/**
 * User: vbedrosova
 * Date: 11.06.2010
 * Time: 12:58:10
 */
public class SwabraRulesTest extends TestCase {
  @NotNull
  private File myBaseDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myBaseDir = new File("baseDir");
  }

  private SwabraRules createRules(@NotNull String... rules) {
    return new SwabraRules(myBaseDir, Arrays.asList(rules));
  }

  @Test
  public void test_no_rules() {
    final SwabraRules rules = createRules();

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
  }

  @Test
  public void test_path_exclude() {
    final SwabraRules rules = createRules("-:some/path");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path")));
    assertFalse(rules.shouldInclude(resolve("some/path/content")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some")));
    assertTrue(rules.shouldInclude(resolve("another/path")));
  }

  @Test
  public void test_path_exclude_dot() {
    final SwabraRules rules = createRules("-:some/path/.");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path")));
    assertFalse(rules.shouldInclude(resolve("some/path/content")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some")));
    assertTrue(rules.shouldInclude(resolve("another/path")));
  }

  @Test
  public void test_mask_exclude() {
    final SwabraRules rules = createRules("-:some/path*");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some")));
    assertTrue(rules.shouldInclude(resolve("some/path/content")));
    assertTrue(rules.shouldInclude(resolve("another/path")));
  }

  @Test
  public void test_path_exclude_star() {
    final SwabraRules rules = createRules("-:some/path/*");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path/content")));

    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content/inner")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some")));
    assertTrue(rules.shouldInclude(resolve("another/path")));
  }

  @Test
  public void test_path_exclude_stars_1() {
    final SwabraRules rules = createRules("-:some/path/**");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path/content")));
    assertFalse(rules.shouldInclude(resolve("some/path/content/inner")));

    assertTrue(rules.shouldInclude(resolve("some/path")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some")));
    assertTrue(rules.shouldInclude(resolve("another/path")));
  }

  @Test
  public void test_path_exclude_stars_2() {
    final SwabraRules rules = createRules("-:**/some/**");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("another/some/content")));

    assertTrue(rules.shouldInclude(resolve("some")));
    assertTrue(rules.shouldInclude(resolve("some/content")));

    assertTrue(rules.shouldInclude(resolve("another/some")));
    assertTrue(rules.shouldInclude(resolve("another/path")));
  }

  @Test
  public void test_path_exclude_stars_3() {
    final SwabraRules rules = createRules("-:**/some");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("another/some")));
    assertFalse(rules.shouldInclude(resolve("another/some/content")));

    assertTrue(rules.shouldInclude(resolve("some")));
    assertTrue(rules.shouldInclude(resolve("some/content")));
  }

  @Test
  public void test_path_include_1() {
    final SwabraRules rules = createRules("some/path");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));
    assertFalse(rules.shouldInclude(new File("some/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content")));
  }

  @Test
  public void test_path_include_2() {
    final SwabraRules rules = createRules("+:some/path");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));
    assertFalse(rules.shouldInclude(new File("some/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content")));
  }

  @Test
  public void test_path_include_dot() {
    final SwabraRules rules = createRules("+:some/path/.");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));
    assertFalse(rules.shouldInclude(new File("some/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content")));
  }

  @Test
  public void test_mask_include() {
    final SwabraRules rules = createRules("some/path*");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));
    assertFalse(rules.shouldInclude(new File("some/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content")));
  }

  @Test
  public void test_path_include_star() {
    final SwabraRules rules = createRules("some/path/*");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));
    assertFalse(rules.shouldInclude(new File("some/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content")));
    assertTrue(rules.shouldInclude(resolve("some/path/content/inner")));
  }

  @Test
  public void test_path_include_stars() {
    final SwabraRules rules = createRules("some/path/**");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));
    assertFalse(rules.shouldInclude(new File("some/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content")));
    assertTrue(rules.shouldInclude(resolve("some/path/content/inner")));
  }

  @Test
  public void test_duplicating_rule_include() {
    final SwabraRules rules = createRules("+:some/path", "+:some/path");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));
    assertFalse(rules.shouldInclude(new File("some/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content")));
  }

  @Test
  public void test_duplicating_rule_include_exclude() {
    final SwabraRules rules = createRules("+:some/path", "-:some/path");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path")));
    assertFalse(rules.shouldInclude(resolve("some/path/content")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some")));
    assertTrue(rules.shouldInclude(resolve("another/path")));
  }

  @Test
  public void test_include_base_dir() {
    final SwabraRules rules = createRules("");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
  }

  @Test
  public void test_include_base_dir_abs() {
    final SwabraRules rules = createRules(getBaseDirPath());

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
  }

  @Test
  public void test_include_base_dir_dot() {
    final SwabraRules rules = createRules("+:.");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
  }

  @Test
  public void test_include_base_dir_star() {
    final SwabraRules rules = createRules("+:*");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
  }

  @Test
  public void test_include_base_dir_stars() {
    final SwabraRules rules = createRules("+:**");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
  }

  @Test
  public void test_exclude_base_dir_dot() {
    final SwabraRules rules = createRules("-:.");

    assertPaths(rules);

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(getBaseDirPath()));
    assertFalse(rules.shouldInclude(resolve("some/path")));
  }

  @Test
  public void test_exclude_base_dir_abs() {
    final SwabraRules rules = createRules("-:" + getBaseDirPath());

    assertPaths(rules);

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(getBaseDirPath()));
    assertFalse(rules.shouldInclude(resolve("some/path")));
  }

  @Test
  public void test_exclude_base_dir_star() {
    final SwabraRules rules = createRules("-:*");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content")));

    assertFalse(rules.shouldInclude(resolve("some")));
  }

  @Test
  public void test_exclude_base_dir_stars() {
    final SwabraRules rules = createRules("-:**");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some")));
    assertFalse(rules.shouldInclude(resolve("some/path")));
    assertFalse(rules.shouldInclude(resolve("some/path/content")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
  }

  @Test
  @TestFor(issues = "TW-14666")
  public void test_partly_exclude_base_dir() {
    final SwabraRules rules = createRules("-:.", "+:some/path");

    assertPaths(rules, resolve("some/path"));

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(getBaseDirPath()));
    assertFalse(rules.shouldInclude(resolve("another/path")));

    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/inner")));
  }


  @Test
  public void test_partly_exclude_base_dir_abs() {
    final SwabraRules rules = createRules("-:" + getBaseDirPath(), "+:some/path");

    assertPaths(rules, resolve("some/path"));

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(getBaseDirPath()));
    assertFalse(rules.shouldInclude(resolve("another/path")));

    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/inner")));
  }

  @Test
  public void test_partly_exclude_base_dir_star() {
    final SwabraRules rules = createRules("-:*", "+:some/path");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some")));
    assertFalse(rules.shouldInclude(resolve("another")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/inner")));
    assertTrue(rules.shouldInclude(resolve("another/path")));
  }

  @Test
  public void test_partly_exclude_base_dir_stars() {
    final SwabraRules rules = createRules("-:**", "+:some/path");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("another/path")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/inner")));
  }

  @Test
  public void test_include_outer_dir_abs() {
    final File outer = new File("outer");
    final String outerAbsolutePath = outer.getAbsolutePath();

    final SwabraRules rules = createRules(outerAbsolutePath);

    assertPaths(rules, getBaseDirPath(), outerAbsolutePath);

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(outerAbsolutePath));
    assertTrue(rules.shouldInclude(resolve(outer, "some/path")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
  }

  @Test
  public void test_include_outer_dir_abs_dot() {
    final File outer = new File("outer");
    final String outerAbsolutePath = outer.getAbsolutePath();

    final SwabraRules rules = createRules(outerAbsolutePath + "/.");

    assertPaths(rules, getBaseDirPath(), outerAbsolutePath);

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(outerAbsolutePath));
    assertTrue(rules.shouldInclude(resolve(outer, "some/path")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
  }

  @Test
  public void test_include_outer_dir_abs_star() {
    final File outer = new File("outer");
    final String outerAbsolutePath = outer.getAbsolutePath();

    final SwabraRules rules = createRules(outerAbsolutePath + "/*");

    assertPaths(rules, getBaseDirPath(), outerAbsolutePath);

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(outerAbsolutePath));
    assertFalse(rules.shouldInclude(resolve(outer, "some/path")));

    assertTrue(rules.shouldInclude(resolve(outer, "some")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
  }

  @Test
  public void test_include_outer_dir_abs_stars() {
    final File outer = new File("outer");
    final String outerAbsolutePath = outer.getAbsolutePath();

    final SwabraRules rules = createRules(outerAbsolutePath + "/**");

    assertPaths(rules, getBaseDirPath(), outerAbsolutePath);

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(outerAbsolutePath));

    assertTrue(rules.shouldInclude(resolve(outer, "some")));
    assertTrue(rules.shouldInclude(resolve(outer, "some/path")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some/path")));
  }

  @Test
  public void test_include_content_1() {
    final SwabraRules rules = createRules("-:some/path", "+:some/path/content");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content")));
    assertTrue(rules.shouldInclude(resolve("some/path/content/inner")));
  }

  @Test
  public void test_include_content_2() {
    final SwabraRules rules = createRules("-:some/path", "+:some/path/content", "-:some/path/content/inner");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content")));
    assertTrue(rules.shouldInclude(resolve("some/path/content/another")));
    assertFalse(rules.shouldInclude(resolve("some/path/content/inner")));
    assertTrue(rules.shouldInclude(resolve("some/path/content/innerAnother")));
    assertFalse(rules.shouldInclude(resolve("some/path/content/inner/content")));
  }

  @Test
  public void test_misc_1() {
    final SwabraRules rules = createRules("-:some/path/**", "+:some/path/content/inner");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path/content")));
    assertFalse(rules.shouldInclude(resolve("some/path/another/path")));

    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content/inner")));
    assertTrue(rules.shouldInclude(resolve("some/path/content/inner/another/path")));
  }

  @Test
  public void test_misc_2() {
    final SwabraRules rules = createRules("-:some/path", "+:some/path/content/inner/**");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path")));
    assertFalse(rules.shouldInclude(resolve("some/path/content")));
    assertFalse(rules.shouldInclude(resolve("some/path/another/path")));
    assertFalse(rules.shouldInclude(resolve("some/path/content/inner")));

    assertTrue(rules.shouldInclude(resolve("some/path/content/inner/another/path")));
  }

  @Test
  public void test_misc_3() {
    final SwabraRules rules = createRules("-:**/some/**", "+:some/path");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude(resolve("another/some/content")));

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(resolve("some")));
    assertTrue(rules.shouldInclude(resolve("some/content")));
    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content")));
    assertTrue(rules.shouldInclude(resolve("another/some")));
    assertTrue(rules.shouldInclude(resolve("another/content")));
  }

  @Test
  @TestFor(issues = "TW-14668")
  public void test_misc_with_dots() {
    final SwabraRules rules = createRules("-:./**/some/**", "+:./some/path");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("another/some/content")));

    assertTrue(rules.shouldInclude(resolve("some")));
    assertTrue(rules.shouldInclude(resolve("some/content")));
    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content")));
    assertTrue(rules.shouldInclude(resolve("another/some")));
    assertTrue(rules.shouldInclude(resolve("another/content")));
  }

  @Test
  public void test_misc_with_absolute() {
    final File outer = new File("outer");
    final String outerAbsolutePath = outer.getAbsolutePath();

    final SwabraRules rules = createRules("-:some/path", "+:some/path/content/inner/**", resolve(outer, "some/path"), "-:" + resolve(outer, "some/path/content"));

    assertPaths(rules, resolve(outer, "some/path"), getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertTrue(rules.shouldInclude(getBaseDirPath()));

    assertFalse(rules.shouldInclude(resolve("some/path")));
    assertFalse(rules.shouldInclude(resolve("some/path/content")));
    assertFalse(rules.shouldInclude(resolve("some/path/another/path")));

    assertFalse(rules.shouldInclude(resolve("some/path/content/inner")));
    assertTrue(rules.shouldInclude(resolve("some/path/content/inner/another/path")));

    assertFalse(rules.shouldInclude(outerAbsolutePath));

    assertTrue(rules.shouldInclude(resolve(outer, "some/path")));
    assertTrue(rules.shouldInclude(resolve(outer, "some/path/another/path")));

    assertFalse(rules.shouldInclude(resolve(outer, "some/path/content")));
    assertFalse(rules.shouldInclude(resolve(outer, "some/path/content/inner")));
  }

  @Test
  public void test_partly_include_1() {
    final SwabraRules rules = createRules("-:some/path/**", "+:some/path/content/inner/**");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path/content")));
    assertFalse(rules.shouldInclude(resolve("some/path/content/another")));

    assertFalse(rules.shouldInclude(resolve("some/path/content/inner")));
    assertFalse(rules.shouldInclude(resolve("some/path/another")));
    assertFalse(rules.shouldInclude(resolve("some/path/another/content")));

    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content/inner/content")));
  }

  @Test
  public void test_partly_include_2() {
    final SwabraRules rules = createRules("-:some/path/**",
      "+:some/path/content/inner/**",
      "-:some/path/content/inner/another/path");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path/content")));
    assertFalse(rules.shouldInclude(resolve("some/path/content/another")));
    assertFalse(rules.shouldInclude(resolve("some/path/content/inner/another/path")));
    assertFalse(rules.shouldInclude(resolve("some/path/content/inner/another/path/content")));
    assertFalse(rules.shouldInclude(resolve("some/path/content/inner")));

    assertTrue(rules.shouldInclude(resolve("some/path")));
    assertTrue(rules.shouldInclude(resolve("some/path/content/inner/content")));
    assertTrue(rules.shouldInclude(resolve("some/path/content/inner/another")));
  }

  @Test
  public void test_partly_include_base_dir() {
    final SwabraRules rules = createRules("+:.", "-:some/path");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path")));
    assertFalse(rules.shouldInclude(resolve("some/path/inner")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("another/path")));
  }

  @Test
  public void test_partly_include_base_dir_star() {
    final SwabraRules rules = createRules("+:*", "-:some/path");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path")));
    assertFalse(rules.shouldInclude(resolve("some/path/inner")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some")));
    assertTrue(rules.shouldInclude(resolve("another/path")));
  }

  @Test
  public void test_partly_include_base_dir_stars() {
    final SwabraRules rules = createRules("+:**", "-:some/path");

    assertPaths(rules, getBaseDirPath());

    assertFalse(rules.shouldInclude("any/path"));
    assertFalse(rules.shouldInclude(new File("any/path").getAbsolutePath()));

    assertFalse(rules.shouldInclude(resolve("some/path")));
    assertFalse(rules.shouldInclude(resolve("some/path/inner")));

    assertTrue(rules.shouldInclude(getBaseDirPath()));
    assertTrue(rules.shouldInclude(resolve("some")));
    assertTrue(rules.shouldInclude(resolve("another/path")));
  }

  @NotNull
  public File getBaseDir() {
    return myBaseDir;
  }

  @NotNull
  public String getBaseDirPath() {
    return getBaseDir().getAbsolutePath();
  }

  private void assertPaths(@NotNull SwabraRules rules, String... expectedPaths) {
    final List<String> paths = getPaths(rules);
    assertEquals(paths.toString(), expectedPaths.length, paths.size());
    for (final String p : expectedPaths) {
      assertNotNull(paths.toString(), CollectionsUtil.findFirst(paths, new Filter<String>() {
        public boolean accept(@NotNull final String data) {
          return FileUtil.normalizeSeparator(data).equals(FileUtil.normalizeSeparator(p));
        }
      }));
    }
  }

  @NotNull
  private List<String> getPaths(@NotNull SwabraRules rules) {
    return CollectionsUtil.convertCollection(rules.getPaths(), new Converter<String, File>() {
      public String createFrom(@NotNull final File source) {
        return source.getPath();
      }
    });
  }

  @NotNull
  private String resolve(@NotNull String path) {
    return resolve(myBaseDir, path);
  }

  @NotNull
  private String resolve(@NotNull File baseDir, @NotNull String path) {
    return FileUtil.resolvePath(baseDir, path).getPath();
  }
}
