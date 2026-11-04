package com.management.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
  void indexes_areCurrent() throws IOException {
    for (Map.Entry<Path, String> entry : IndexGenerator.expected(ROOT).entrySet()) {
      assertThat(text(entry.getKey()))
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
  }

  static Stream<Path> workflowFiles() throws IOException {
    try (Stream<Path> paths = Files.walk(WORKFLOW)) {
      return paths.filter(path -> path.toString().endsWith(".md")).sorted().toList().stream();
    }
  }

  static Stream<Path> genericFiles() throws IOException {
    return workflowFiles().filter(path -> !isTechnologyFile(path));
  }

  private static boolean isTechnologyFile(Path path) {
    String top = WORKFLOW.relativize(path).getName(0).toString();
    return IndexGenerator.TECHNOLOGY_CATEGORIES.contains(top);
  }

  private static List<String> found(List<String> terms, String text) {
    return terms.stream()
        .filter(
            term ->
                Pattern.compile("(?<!\\w)" + Pattern.quote(term) + "(?![\\w-])")
                    .matcher(text)
                    .find())
        .toList();
  }

  /** Lower-cased text, without the README's generated catalogue of technology paths. */
  private static String generic(Path file) throws IOException {
    String text = text(file).toLowerCase(Locale.ROOT);
    if (file.equals(WORKFLOW.resolve("README.md"))) {
      int start = text.indexOf("## catalogue");
      int end = text.indexOf("## [", start);
      return text.substring(0, start) + text.substring(end);
    }
    return text;
  }

  private static String text(Path file) throws IOException {
    return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
  }
}
