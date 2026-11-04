package com.management.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexGeneratorTest {

  private static final String VALID_FILE =
      """
      # Topic

      Applies when: testing

      ## Index

      ## [one] First
      """;

  private static final String EXPECTED_HEAD = "# T

## Index

- [b] Second
- [a] First

## [b]";

  private Path root;

  @BeforeEach
  void setUp(@TempDir Path directory) {
    root = directory;
  }

  @Test
  void withIndex_listsEveryTaggedSectionInOrder() {
    String text = "# T\n\n## Index\n\nstale\n\n## [b] Second\n\nx\n\n## [a] First\n";

    assertThat(IndexGenerator.withIndex(text)).startsWith(EXPECTED_HEAD).endsWith("## [a] First
");
  }

  @Test
  void withIndex_ignoresHeadingsInsideCodeFences() {
    String text = "## Index\n\n## [a] First\n\n```\n## not a heading\n```\n";

    assertThat(IndexGenerator.withIndex(text)).contains("- [a] First").doesNotContain("- [not");
  }

  @Test
  void withIndex_whenHeadingUntagged_refuses() {
    assertThatThrownBy(() -> IndexGenerator.withIndex("## Index\n\n## Plain\n"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("untagged headings: ## Plain");
  }

  @Test
  void withIndex_whenTagRepeated_refuses() {
    assertThatThrownBy(() -> IndexGenerator.withIndex("## Index\n\n## [a] One\n\n## [a] Two\n"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate tag: a");
  }

  @Test
  void withIndex_whenIndexHeadingMissing_refuses() {
    assertThatThrownBy(() -> IndexGenerator.withIndex("## [a] One\n"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing '## Index'");
  }

  @Test
  void withCatalogue_listsEachFileWithItsTags() {
    String catalogued =
        IndexGenerator.withCatalogue(
            "## Catalogue\n\n## [next] Next\n", Map.of("a/b.md", "## [x] X\n\n## [y] Y\n"));

    assertThat(catalogued).contains("- `a/b.md`: [x] [y]").contains("## [next] Next");
  }

  @Test
  void structureProblems_whenScaffoldIsValid_findsNothing() throws IOException {
    write("design/a.md", VALID_FILE);
    write(
        "languages/java/design/b.md",
        VALID_FILE.replace("## Index", "Builds on: `design/a.md`\n\n## Index"));

    assertThat(IndexGenerator.structureProblems(root)).isEmpty();
  }

  @Test
  void structureProblems_whenTechnologyFolderHasNoTopLevelConcern_reportsIt() throws IOException {
    write(
        "languages/java/invented/b.md",
        VALID_FILE.replace("## Index", "Builds on: `x`\n\n## Index"));

    assertThat(IndexGenerator.structureProblems(root))
        .containsExactly("languages/java/invented: no top-level");
  }

  @Test
  void structureProblems_whenTechnologyFileHasNoParent_reportsIt() throws IOException {
    write("design/a.md", VALID_FILE);
    write("languages/java/design/b.md", VALID_FILE);

    assertThat(IndexGenerator.structureProblems(root))
        .containsExactly("languages/java/design/b.md: missing 'Builds on:'");
  }

  @Test
  void structureProblems_whenAppliesWhenMissing_reportsIt() throws IOException {
    write("design/a.md", "# Topic\n\n## Index\n");

    assertThat(IndexGenerator.structureProblems(root))
        .containsExactly("design/a.md: missing 'Applies when:'");
  }

  @Test
  void structureProblems_whenReferenceIsBroken_reportsIt() throws IOException {
    write("design/a.md", VALID_FILE + "\nSee `design/gone.md`.\n");

    assertThat(IndexGenerator.structureProblems(root))
        .containsExactly("design/a.md: broken reference design/gone.md");
  }

  @Test
  void structureProblems_whenDocHasEmDash_reportsIt() throws IOException {
    write("design/a.md", VALID_FILE + "\nOne — two.\n");

    List<String> problems = IndexGenerator.structureProblems(root);

    assertThat(problems).hasSize(1);
    assertThat(problems.get(0)).endsWith("a.md: contains an em dash");
  }

  private void write(String relative, String text) throws IOException {
    Path path = root.resolve(IndexGenerator.WORKFLOW).resolve(relative);
    Files.createDirectories(path.getParent());
    Files.writeString(path, text, StandardCharsets.UTF_8);
  }
}
