/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import jetbrains.buildServer.swabra.snapshots.SwabraRules;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/**
 * User: vbedrosova
 * Date: 11.06.2010
 * Time: 12:58:10
 */
public class SwabraRulesTest extends TestCase {
  private SwabraRules createRules(String... rules) {
    return new SwabraRules(Arrays.asList(rules));
  }

  private SwabraRules createRules(File baseDir, String... rules) {
    return new SwabraRules(baseDir, Arrays.asList(rules));
  }

  @Test
  public void test_no_rules() {
    final SwabraRules rules = createRules();

    assertTrue(rules.shouldInclude("."));
    assertTrue(rules.shouldInclude("any/path"));
  }

  @Test
  public void test_path_exclude() {
    final SwabraRules rules = createRules("-:some/path");

    assertTrue(rules.shouldInclude("."));

    assertFalse(rules.shouldInclude("some/path"));
    assertFalse(rules.shouldInclude("some/path/content"));

    assertTrue(rules.shouldInclude("some/"));
    assertTrue(rules.shouldInclude("some"));

    assertTrue(rules.shouldInclude("another/path"));
  }

  @Test
  @TestFor(issues = "TW-14666")
  public void test_path_exclude_1() {
    final SwabraRules rules = createRules("-:.", "+:some/path");

    assertFalse(rules.shouldInclude("."));

    assertTrue(rules.shouldInclude("some/path"));
    assertTrue(rules.shouldInclude("some/path/content"));

    assertFalse(rules.shouldInclude("another/some/path"));
  }

  @Test
  public void test_path_include_1() {
    final SwabraRules rules = createRules("+:some/path");

    assertFalse(rules.shouldInclude("."));

    assertTrue(rules.shouldInclude("some/path"));
    assertTrue(rules.shouldInclude("some/path/content"));

    assertFalse(rules.shouldInclude("another/some/path"));
  }

  @Test
  public void test_path_include_2() {
    final SwabraRules rules = createRules("some/path");

    assertFalse(rules.shouldInclude("."));

    assertTrue(rules.shouldInclude("some/path"));
    assertTrue(rules.shouldInclude("some/path/content"));

    assertFalse(rules.shouldInclude("another/some/path"));
  }

  @Test
  public void test_include_content_1() {
    final SwabraRules rules = createRules("-:some/path", "+:some/path/content");

    assertFalse(rules.shouldInclude("."));

    assertFalse(rules.shouldInclude("some/path"));
    assertTrue(rules.shouldInclude("some/path/content"));

    assertFalse(rules.shouldInclude("another/some/path"));
  }

  @Test
  public void test_include_content_2() {
    final SwabraRules rules = createRules("-:some/path", "+:some/path/content", "-:some/path/content/inner");

    assertFalse(rules.shouldInclude("."));

    assertFalse(rules.shouldInclude("some/path"));
    assertTrue(rules.shouldInclude("some/path/content"));
    assertFalse(rules.shouldInclude("some/path/content/inner"));

    assertFalse(rules.shouldInclude("another/some/path"));
  }

  @Test
  public void test_mask_exclude_1() {
    final SwabraRules rules = createRules("-:**/some/**");

    assertTrue(rules.shouldInclude("."));

    assertTrue(rules.shouldInclude("some"));
    assertTrue(rules.shouldInclude("some/content"));

    assertTrue(rules.shouldInclude("another/some"));
    assertFalse(rules.shouldInclude("another/some/content"));

    assertTrue(rules.shouldInclude("another/path"));
  }

  @Test
  public void test_mask_exclude_2() {
    final SwabraRules rules = createRules("-:some/path/**");

    assertTrue(rules.shouldInclude("."));

    assertTrue(rules.shouldInclude("some/path"));
    assertFalse(rules.shouldInclude("some/path/content"));

    assertTrue(rules.shouldInclude("some"));
    assertTrue(rules.shouldInclude("another/some/path/content"));
  }

  @Test
  public void test_mask_exclude_3() {
    final SwabraRules rules = createRules("-:**/some");

    assertTrue(rules.shouldInclude("."));

    assertTrue(rules.shouldInclude("some"));
    assertFalse(rules.shouldInclude("another/some"));

    assertTrue(rules.shouldInclude("some/content"));
    assertFalse(rules.shouldInclude("another/some/content"));
  }

  @Test
  public void test_mask_exclude_4() {
    final SwabraRules rules = createRules("-:**");

    assertFalse(rules.shouldInclude("."));

    assertFalse(rules.shouldInclude("some/path"));
    assertFalse(rules.shouldInclude("another/path"));
  }

  @Test
  public void test_mask_include_content_1() {
    final SwabraRules rules = createRules("-:some/path/**", "+:some/path/content/inner/**");

    assertFalse(rules.shouldInclude("."));

    assertFalse(rules.shouldInclude("some/path"));
    assertFalse(rules.shouldInclude("some/path/content"));
    assertFalse(rules.shouldInclude("some/path/content/another"));

    assertFalse(rules.shouldInclude("some/path/content/inner"));
    assertTrue(rules.shouldInclude("some/path/content/inner/content"));

    assertFalse(rules.shouldInclude("some/path/another"));
    assertFalse(rules.shouldInclude("some/path/another/content"));
  }

  @Test
  public void test_mask_include_content_2() {
    final SwabraRules rules = createRules("-:some/path/**",
      "+:some/path/content/inner/**",
      "-:some/path/content/inner/another/path");

    assertFalse(rules.shouldInclude("."));

    assertFalse(rules.shouldInclude("some/path"));
    assertFalse(rules.shouldInclude("some/path/content"));
    assertFalse(rules.shouldInclude("some/path/content/another"));
    assertFalse(rules.shouldInclude("some/path/content/inner/another/path"));
    assertFalse(rules.shouldInclude("some/path/content/inner/another/path/content"));

    assertFalse(rules.shouldInclude("some/path/content/inner"));
    assertTrue(rules.shouldInclude("some/path/content/inner/content"));
    assertTrue(rules.shouldInclude("some/path/content/inner/another"));
  }

  @Test
  public void test_misc_1() {
    final SwabraRules rules = createRules("-:some/path/**", "+:some/path/content/inner");

    assertFalse(rules.shouldInclude("."));

    assertFalse(rules.shouldInclude("some/path"));
    assertFalse(rules.shouldInclude("some/path/content"));
    assertFalse(rules.shouldInclude("some/path/another/path"));

    assertTrue(rules.shouldInclude("some/path/content/inner"));
    assertTrue(rules.shouldInclude("some/path/content/inner/another/path"));
  }

  @Test
  public void test_misc_2() {
    final SwabraRules rules = createRules("-:some/path", "+:some/path/content/inner/**");

    assertFalse(rules.shouldInclude("."));

    assertFalse(rules.shouldInclude("some/path"));
    assertFalse(rules.shouldInclude("some/path/content"));
    assertFalse(rules.shouldInclude("some/path/another/path"));

    assertFalse(rules.shouldInclude("some/path/content/inner"));
    assertTrue(rules.shouldInclude("some/path/content/inner/another/path"));
  }


  @Test
  public void test_misc_3() {
    final SwabraRules rules = createRules("-:**/some/**", "+:some/path");

    assertFalse(rules.shouldInclude("."));

    assertFalse(rules.shouldInclude("some"));
    assertFalse(rules.shouldInclude("some/content"));
    assertFalse(rules.shouldInclude("another/some"));
    assertFalse(rules.shouldInclude("another/some/content"));

    assertFalse(rules.shouldInclude("another/content"));
    assertTrue(rules.shouldInclude("some/path"));
    assertTrue(rules.shouldInclude("some/path/content"));
  }

  @Test
  @TestFor(issues = "TW-14668")
  public void test_misc_with_dots() {
    final SwabraRules rules = createRules("-:./**/some/**", "+:./some/path");

    assertFalse(rules.shouldInclude("."));

    assertFalse(rules.shouldInclude("some"));
    assertFalse(rules.shouldInclude("some/content"));
    assertFalse(rules.shouldInclude("another/some"));
    assertFalse(rules.shouldInclude("another/some/content"));

    assertFalse(rules.shouldInclude("another/content"));
    assertTrue(rules.shouldInclude("some/path"));
    assertTrue(rules.shouldInclude("some/path/content"));
  }

  @Test
  public void test_misc_with_absolute() {
    final File baseDir = new File("baseDir");
    final File outerDir = new File("outerDir");
    final SwabraRules rules = createRules(baseDir, "-:some/path", "+:some/path/content/inner/**", resolve("some/path", outerDir), "-:" + resolve("some/path/content", outerDir));

    assertFalse(rules.shouldInclude(baseDir.getPath()));

    assertFalse(rules.shouldInclude(resolve("some/path", baseDir)));
    assertFalse(rules.shouldInclude(resolve("some/path/content", baseDir)));
    assertFalse(rules.shouldInclude(resolve("some/path/another/path", baseDir)));

    assertFalse(rules.shouldInclude(resolve("some/path/content/inner", baseDir)));
    assertTrue(rules.shouldInclude(resolve("some/path/content/inner/another/path", baseDir)));

    assertFalse(rules.shouldInclude(outerDir.getPath()));

    assertTrue(rules.shouldInclude(resolve("some/path", outerDir)));
    assertTrue(rules.shouldInclude(resolve("some/path/another/path", outerDir)));

    assertFalse(rules.shouldInclude(resolve("some/path/content", outerDir)));
    assertFalse(rules.shouldInclude(resolve("some/path/content/inner", outerDir)));
  }

  @NotNull
  private String resolve(@NotNull String path, @NotNull File baseDir) {
    return FileUtil.resolvePath(baseDir, path).getPath();
  }

//  @Test
//  public void test_crazy_1() {
//    final SwabraRules rules = createRules("-:some/path", "+:some/path/**");
//
//    assertFalse(rules.shouldInclude("."));
//
//    assertFalse(rules.shouldInclude("some/path"));
//    assertTrue(rules.shouldInclude("some/path/content"));
//    assertTrue(rules.shouldInclude("some/path/another/path"));
//  }
}
