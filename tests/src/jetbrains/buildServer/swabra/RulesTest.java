package jetbrains.buildServer.swabra;

import jetbrains.buildServer.swabra.snapshots.rules.Rules;
import junit.framework.TestCase;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * User: vbedrosova
 * Date: 11.06.2010
 * Time: 12:58:10
 */
public class RulesTest extends TestCase {
  @Test
  public void test_no_rules() {
    final Rules rules = new Rules(Collections.<String>emptyList());

    assertFalse(rules.exclude("."));
    assertFalse(rules.exclude("any/path"));
    assertFalse(rules.exclude("any/path/**/?*.ext"));
  }

  @Test
  public void test_path_exclude() {
    final Rules rules = new Rules(Arrays.asList("-:some/path"));

    assertFalse(rules.exclude("."));

    assertTrue(rules.exclude("some/path"));
    assertTrue(rules.exclude("some/path/content"));

    assertFalse(rules.exclude("some/"));
    assertFalse(rules.exclude("some"));
    assertFalse(rules.exclude("another/path"));
  }

  //http://youtrack.jetbrains.net/issue/TW-14666
  @Test
  public void test_path_exclude_1() {
    final Rules rules = new Rules(Arrays.asList("-:.", "+:some/path"));

    assertTrue(rules.exclude("."));
    assertFalse(rules.exclude("some/path"));
    assertFalse(rules.exclude("some/path/content"));
    assertTrue(rules.exclude("another/some/path"));
  }

  @Test
  public void test_path_include_1() {
    final Rules rules = new Rules(Arrays.asList("+:some/path"));

    assertFalse(rules.exclude("."));
    assertFalse(rules.exclude("some/path"));
    assertFalse(rules.exclude("some/path/content"));
  }

  @Test
  public void test_path_include_2() {
    final Rules rules = new Rules(Arrays.asList("some/path"));

    assertFalse(rules.exclude("."));
    assertFalse(rules.exclude("some/path"));
    assertFalse(rules.exclude("some/path/content"));
  }

  @Test
  public void test_include_content_1() {
    final Rules rules = new Rules(Arrays.asList("-:some/path",
      "+:some/path/content"));

    assertFalse(rules.exclude("."));
    assertTrue(rules.exclude("some/path"));
    assertFalse(rules.exclude("some/path/content"));
  }

  @Test
  public void test_include_content_2() {
    final Rules rules = new Rules(Arrays.asList("-:some/path",
      "+:some/path/content",
      "-:some/path/content/inner"));

    assertFalse(rules.exclude("."));
    assertTrue(rules.exclude("some/path"));
    assertFalse(rules.exclude("some/path/content"));
    assertTrue(rules.exclude("some/path/content/inner"));
  }

  @Test
  public void test_mask_exclude_1() {
    final Rules rules = new Rules(Arrays.asList("-:**/some/**"));

    assertFalse(rules.exclude("."));
    assertTrue(rules.exclude("some"));
    assertTrue(rules.exclude("some/content"));
    assertTrue(rules.exclude("another/some"));
    assertTrue(rules.exclude("another/some/content"));

    assertFalse(rules.exclude("another/content"));
  }

  @Test
  public void test_mask_exclude_2() {
    final Rules rules = new Rules(Arrays.asList("-:some/path/**"));

    assertFalse(rules.exclude("."));

    assertTrue(rules.exclude("some/path"));
    assertTrue(rules.exclude("some/path/content"));

    assertFalse(rules.exclude("some"));
    assertFalse(rules.exclude("another/some/path/content"));
  }

  @Test
  public void test_mask_exclude_3() {
    final Rules rules = new Rules(Arrays.asList("-:**/some"));

    assertFalse(rules.exclude("."));

    assertTrue(rules.exclude("some"));
    assertTrue(rules.exclude("another/some"));

    assertFalse(rules.exclude("some/content"));
    assertFalse(rules.exclude("another/some/content"));
  }

  @Test
  public void test_mask_exclude_4() {
    final Rules rules = new Rules(Arrays.asList("-:**"));

    assertTrue(rules.exclude("."));

    assertTrue(rules.exclude("some/path"));
    assertTrue(rules.exclude("another/path"));
  }

  @Test
  public void test_mask_include_content_1() {
    final Rules rules = new Rules(Arrays.asList("-:some/path/**",
      "+:some/path/content/inner/**"));

    assertFalse(rules.exclude("."));

    assertTrue(rules.exclude("some/path"));
    assertTrue(rules.exclude("some/path/content"));
    assertTrue(rules.exclude("some/path/content/another"));

    assertFalse(rules.exclude("some/path/content/inner"));
    assertFalse(rules.exclude("some/path/content/inner/content"));
  }

  @Test
  public void test_mask_include_content_2() {
    final Rules rules = new Rules(Arrays.asList("-:some/path/**",
      "+:some/path/content/inner/**",
      "-:some/path/content/inner/another/path"));

    assertFalse(rules.exclude("."));

    assertTrue(rules.exclude("some/path"));
    assertTrue(rules.exclude("some/path/content"));
    assertTrue(rules.exclude("some/path/content/another"));
    assertTrue(rules.exclude("some/path/content/inner/another/path"));
    assertTrue(rules.exclude("some/path/content/inner/another/path/content"));

    assertFalse(rules.exclude("some/path/content/inner"));
    assertFalse(rules.exclude("some/path/content/inner/content"));
    assertFalse(rules.exclude("some/path/content/inner/another"));
  }

  @Test
  public void test_misc_1() {
    final Rules rules = new Rules(Arrays.asList("-:some/path/**",
      "+:some/path/content/inner"));

    assertFalse(rules.exclude("."));

    assertTrue(rules.exclude("some/path"));
    assertTrue(rules.exclude("some/path/content"));
    assertTrue(rules.exclude("some/path/another/path"));

    assertFalse(rules.exclude("some/path/content/inner"));
    assertFalse(rules.exclude("some/path/content/inner/another/path"));
  }

  @Test
  public void test_misc_2() {
    final Rules rules = new Rules(Arrays.asList("-:some/path",
      "+:some/path/content/inner/**"));

    assertFalse(rules.exclude("."));

    assertTrue(rules.exclude("some/path"));
    assertTrue(rules.exclude("some/path/content"));
    assertTrue(rules.exclude("some/path/another/path"));

    assertFalse(rules.exclude("some/path/content/inner"));
    assertFalse(rules.exclude("some/path/content/inner/another/path"));
  }


  @Test
  public void test_misc_3() {
    final Rules rules = new Rules(Arrays.asList("-:**/some/**",
      "+:some/path"));

    assertFalse(rules.exclude("."));

    assertTrue(rules.exclude("some"));
    assertTrue(rules.exclude("some/content"));
    assertTrue(rules.exclude("another/some"));
    assertTrue(rules.exclude("another/some/content"));

    assertFalse(rules.exclude("another/content"));
    assertFalse(rules.exclude("some/path"));
    assertFalse(rules.exclude("some/path/content"));
  }

  @Test
  public void test_crazy_1() {
    final Rules rules = new Rules(Arrays.asList("-:some/path",
      "+:some/path/**"));

    assertFalse(rules.exclude("."));

    assertFalse(rules.exclude("some/path"));
    assertFalse(rules.exclude("some/path/content"));
    assertFalse(rules.exclude("some/path/another/path"));
  }
}
