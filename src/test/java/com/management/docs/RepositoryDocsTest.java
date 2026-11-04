package com.management.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.InputSource;

/**
 * Keeps the docs in this repository honest: indexes current, scaffold intact, workflow generic.
 *
 * <p>A word list only catches known terms. It reduces the risk of a leak and proves nothing more.
 */
class RepositoryDocsTest {

  private static final Path ROOT = Path.of("").toAbsolutePath();
  private static final Path WORKFLOW = ROOT.resolve(IndexGenerator.WORKFLOW);
  private static final List<String> PROJECT_TERMS =
      List.of(
          "kumite",
          "karate",
          "championship",
          "ippon",
          "waza-ari",
          "wazari",
          "yuko",
          "hansoku",
          "senshu",
          "kiken",
          "com.management",
          "com/management");
  private static final List<String> STACK_TERMS =
      List.of(
          "java",
          "spring",
          "spring boot",
          "maven",
          "mongodb",
          "junit",
          "mockito",
          "testcontainers",
          "archunit",
          "checkstyle",
          "spotless",
          "python",
          "ffmpeg");

  @Test
  void javaVersionFile_matchesThePom() throws IOException, XPathExpressionException {
    String pomVersion =
        XPathFactory.newInstance()
            .newXPath()
            .evaluate(
                "/*[local-name()='project']/*[local-name()='properties']"
                    + "/*[local-name()='java.version']",
                new InputSource(ROOT.resolve("pom.xml").toUri().toString()));

    assertThat(Files.readString(ROOT.resolve(".java-version")).strip())
        .as(".java-version feeds CI, the pom property feeds the build: they must agree")
        .isEqualTo(pomVersion);
  }

  @Test
  void indexes_areCurrent() throws IOException {
    for (Map.Entry<Path, String> entry : IndexGenerator.expected(ROOT).entrySet()) {
      assertThat(IndexGenerator.read(entry.getKey()))
          .as("stale index in %s, run the index generator", ROOT.relativize(entry.getKey()))
          .isEqualTo(entry.getValue());
    }
  }

  @Test
  void scaffold_hasNoStructureProblems() throws IOException {
    assertThat(IndexGenerator.structureProblems(ROOT)).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("workflowFiles")
  void workflowFile_namesNoProjectTerm(Path file) throws IOException {
    assertThat(found(PROJECT_TERMS, generic(file))).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("genericFiles")
  void fileOutsideTechnologyFolders_namesNoStack(Path file) throws IOException {
    assertThat(found(STACK_TERMS, generic(file))).isEmpty();
  }

  @Test
  void found_matchesWholeTermsOnly() {
    assertThat(found(List.of("java"), "written in java here")).containsExactly("java");
    assertThat(found(List.of("java"), "javascript and javadoc")).isEmpty();
    assertThat(found(List.of("spring"), "a spring-boot service")).containsExactly("spring");
  }

  static Stream<Path> workflowFiles() throws IOException {
    return IndexGenerator.markdownUnder(WORKFLOW).stream();
  }

  static Stream<Path> genericFiles() throws IOException {
    return workflowFiles()
        .filter(path ->
            !IndexGenerator.isTechnologyFile(
                WORKFLOW.relativize(path).toString().replace('\\', '/')));
  }

  private static List<String> found(List<String> terms, String text) {
    return terms.stream()
        .filter(term ->
            Pattern.compile("(?<!\\w)" + Pattern.quote(term) + "(?!\\w)").matcher(text).find())
        .toList();
  }

  /** Lower-cased text, without the README's generated catalogue of technology paths. */
  private static String generic(Path file) throws IOException {
    String text = IndexGenerator.read(file).toLowerCase(Locale.ROOT);
    if (file.equals(WORKFLOW.resolve("README.md"))) {
      int start = text.indexOf("## catalogue");
      int end = text.indexOf("## [", start);
      return text.substring(0, start) + text.substring(end);
    }
    return text;
  }
}
