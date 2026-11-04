package com.management.docs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates the indexes agents grep, and checks the structure of the workflow scaffold.
 *
 * <p>Every indexed file gets a {@code ## Index} built from its {@code ## [tag] Title} headings, and
 * the workflow README also gets a {@code ## Catalogue} of every workflow file with its tags.
 *
 * <p>Run from the repository root: {@code java
 * src/test/java/com/management/docs/IndexGenerator.java}. {@code RepositoryDocsTest} fails the
 * build on a stale index. It stays one file with only JDK imports, because the source launcher can
 * run nothing else.
 */
public final class IndexGenerator {

  static final String WORKFLOW = "context/workflow";
  static final List<String> TECHNOLOGY_CATEGORIES =
      List.of("languages", "web-frameworks", "databases");
  private static final String TEMPLATE = "TEMPLATE.md";
  private static final String README = "README.md";
  private static final String ROUTER = "CLAUDE.md";
  private static final String FENCE = "```";
  private static final String EM_DASH = "—";
  private static final Set<String> PLAIN_HEADINGS = Set.of("## Index", "## Catalogue");
  private static final Pattern SECTION = Pattern.compile("^## \\[([a-z0-9-]+)\\] (.+)$");
  private static final Pattern DOC_PATH = Pattern.compile("`([A-Za-z0-9_./-]+\\.md)`");
  private static final Pattern TAG_REFERENCE =
      Pattern.compile("`\\[([a-z0-9-]+)\\]` in\\s+`([A-Za-z0-9_./-]+\\.md)`");

  private IndexGenerator() {
    throw new UnsupportedOperationException("static entry point");
  }

  /** Writes indexes, then reports structure problems. Exits non-zero if there are any. */
  public static void main(String[] args) throws IOException {
    Path root = Path.of("").toAbsolutePath();
    if (!Files.isDirectory(root.resolve(WORKFLOW))) {
      System.out.println("run from the repository root: " + WORKFLOW + " not found");
      System.exit(1);
    }
    for (Map.Entry<Path, String> entry : expected(root).entrySet()) {
      if (!read(entry.getKey()).equals(entry.getValue())) {
        System.out.println("updated index: " + root.relativize(entry.getKey()));
        Files.writeString(entry.getKey(), entry.getValue(), StandardCharsets.UTF_8);
      }
    }
    List<String> problems = structureProblems(root);
    problems.forEach(System.out::println);
    System.exit(problems.isEmpty() ? 0 : 1);
  }

  /** Returns the expected content of every generated file. */
  static Map<Path, String> expected(Path root) throws IOException {
    Map<Path, String> expected = new LinkedHashMap<>();
    for (Path path : indexedFiles(root)) {
      try {
        expected.put(path, withIndex(read(path)));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(root.relativize(path) + ": " + e.getMessage(), e);
      }
    }
    Path workflow = root.resolve(WORKFLOW);
    Path readme = workflow.resolve(README);
    Map<String, String> entries = new TreeMap<>();
    expected.forEach(
        (path, text) -> {
          if (path.startsWith(workflow) && !path.equals(readme)) {
            entries.put(workflow.relativize(path).toString().replace('\\', '/'), text);
          }
        });
    expected.computeIfPresent(readme, (path, text) -> withCatalogue(text, entries));
    return expected;
  }

  /** Returns every way the workflow scaffold and the docs break the rules. */
  static List<String> structureProblems(Path root) throws IOException {
    List<String> problems = new ArrayList<>();
    Path workflow = root.resolve(WORKFLOW);
    problems.addAll(technologyFolderProblems(workflow));
    for (Path path : markdownUnder(workflow)) {
      String relative = workflow.relativize(path).toString().replace('\\', '/');
      if (relative.equals(TEMPLATE)) {
        continue;
      }
      String text = read(path);
      if (!relative.equals(README) && !text.contains("\nApplies when: ")) {
        problems.add(relative + ": missing 'Applies when:'");
      }
      if (isTechnologyFile(relative) && !text.contains("\nBuilds on: ")) {
        problems.add(relative + ": missing 'Builds on:'");
      }
      problems.addAll(brokenReferences(relative, text, List.of(workflow, root)));
    }
    if (Files.exists(root.resolve(ROUTER))) {
      problems.addAll(
          brokenReferences(ROUTER, read(root.resolve(ROUTER)), List.of(workflow, root)));
    }
    for (Path readme : domainReadmes(root)) {
      Path folder = readme.resolveSibling("");
      List<Path> bases = List.of(folder, folder.resolve(".."), root, workflow);
      problems.addAll(brokenReferences(root.relativize(readme).toString(), read(readme), bases));
    }
    for (Path path : emDashScope(root)) {
      if (read(path).contains(EM_DASH)) {
        problems.add(root.relativize(path) + ": contains an em dash");
      }
    }
    return problems;
  }

  /** Returns the text with its index regenerated. */
  static String withIndex(String text) {
    List<String> headings = headings(text);
    List<String> untagged =
        headings.stream()
            .filter(line -> !SECTION.matcher(line).matches() && !PLAIN_HEADINGS.contains(line))
            .toList();
    if (!untagged.isEmpty()) {
      throw new IllegalArgumentException("untagged headings: " + String.join(", ", untagged));
    }
    Set<String> seen = new HashSet<>();
    StringBuilder index = new StringBuilder();
    for (String line : headings) {
      Matcher matcher = SECTION.matcher(line);
      if (matcher.matches()) {
        if (!seen.add(matcher.group(1))) {
          throw new IllegalArgumentException("duplicate tag: " + matcher.group(1));
        }
        index.append("- [").append(matcher.group(1)).append("] ").append(matcher.group(2));
        index.append('\n');
      }
    }
    return replaceSection(text, "Index", index.toString().stripTrailing());
  }

  /** Returns the workflow README text with each {@code path -> file text} entry catalogued. */
  static String withCatalogue(String text, Map<String, String> entries) {
    String body =
        entries.entrySet().stream()
            .map(entry -> "- `" + entry.getKey() + "`: " + tags(entry.getValue()))
            .collect(Collectors.joining("\n"));
    return replaceSection(text, "Catalogue", body);
  }

  private static String tags(String text) {
    return headings(text).stream()
        .map(SECTION::matcher)
        .filter(Matcher::matches)
        .map(matcher -> "[" + matcher.group(1) + "]")
        .collect(Collectors.joining(" "));
  }

  /** Returns the {@code ## } heading lines outside code fences. */
  private static List<String> headings(String text) {
    List<String> found = new ArrayList<>();
    boolean inFence = false;
    for (String line : text.split("\n", -1)) {
      if (line.startsWith(FENCE)) {
        inFence = !inFence;
      } else if (!inFence && line.startsWith("## ")) {
        found.add(line);
      }
    }
    return found;
  }

  /** Replaces the content under a heading, up to the next {@code ## } heading. */
  private static String replaceSection(String text, String heading, String body) {
    String target = "## " + heading;
    List<String> lines = List.of(text.split("\n", -1));
    int start = -1;
    int end = lines.size();
    boolean inFence = false;
    for (int number = 0; number < lines.size(); number++) {
      String line = lines.get(number);
      if (line.startsWith(FENCE)) {
        inFence = !inFence;
      } else if (!inFence && line.startsWith("## ")) {
        if (start >= 0) {
          end = number;
          break;
        }
        if (line.equals(target)) {
          start = number;
        }
      }
    }
    if (start < 0) {
      throw new IllegalArgumentException("missing '" + target + "'");
    }
    List<String> replaced = new ArrayList<>(lines.subList(0, start));
    replaced.addAll(List.of(target, "", body, ""));
    replaced.addAll(lines.subList(end, lines.size()));
    return String.join("\n", replaced).stripTrailing() + "\n";
  }

  private static List<String> technologyFolderProblems(Path workflow) throws IOException {
    Set<String> concerns = concerns(workflow);
    List<String> problems = new ArrayList<>();
    for (String category : TECHNOLOGY_CATEGORIES) {
      for (Path technology : directories(workflow.resolve(category))) {
        for (Path concern : directories(technology)) {
          String name = concern.getFileName().toString();
          if (!concerns.contains(name)) {
            problems.add(category + "/" + technology.getFileName() + "/" + name + ": no top-level");
          }
        }
      }
    }
    return problems;
  }

  private static Set<String> concerns(Path workflow) throws IOException {
    return directories(workflow).stream()
        .map(path -> path.getFileName().toString())
        .filter(name -> !TECHNOLOGY_CATEGORIES.contains(name))
        .collect(Collectors.toSet());
  }

  static boolean isTechnologyFile(String relative) {
    return TECHNOLOGY_CATEGORIES.stream().anyMatch(category -> relative.startsWith(category + "/"));
  }

  private static List<String> brokenReferences(String label, String text, List<Path> bases)
      throws IOException {
    List<String> problems = new ArrayList<>();
    Matcher paths = DOC_PATH.matcher(text);
    while (paths.find()) {
      if (candidates(paths.group(1), bases).isEmpty()) {
        problems.add(label + ": broken reference " + paths.group(1));
      }
    }
    Matcher tags = TAG_REFERENCE.matcher(text);
    while (tags.find()) {
      String heading = "\n## [" + tags.group(1) + "] ";
      boolean found = false;
      for (Path candidate : candidates(tags.group(2), bases)) {
        found = found || read(candidate).contains(heading);
      }
      if (!found) {
        problems.add(label + ": broken reference [" + tags.group(1) + "] in " + tags.group(2));
      }
    }
    return problems;
  }

  private static List<Path> candidates(String reference, List<Path> bases) {
    return bases.stream().map(base -> base.resolve(reference)).filter(Files::exists).toList();
  }

  private static List<Path> indexedFiles(Path root) throws IOException {
    List<Path> files = new ArrayList<>();
    for (Path path : markdownUnder(root.resolve(WORKFLOW))) {
      if (!path.getFileName().toString().equals(TEMPLATE)) {
        files.add(path);
      }
    }
    files.addAll(domainReadmes(root));
    return files;
  }

  private static List<Path> domainReadmes(Path root) throws IOException {
    List<Path> files = new ArrayList<>(readmes(root.resolve("src")));
    files.addAll(readmes(root.resolve("config")));
    return files;
  }

  private static List<Path> emDashScope(Path root) throws IOException {
    List<Path> files = new ArrayList<>(markdownUnder(root.resolve(WORKFLOW)));
    files.addAll(domainReadmes(root));
    for (String name : List.of(ROUTER, README, "CONTRIBUTING.md")) {
      if (Files.exists(root.resolve(name))) {
        files.add(root.resolve(name));
      }
    }
    return files;
  }

  private static List<Path> readmes(Path directory) throws IOException {
    return markdownUnder(directory).stream()
        .filter(path -> path.getFileName().toString().equals(README))
        .toList();
  }

  static List<Path> markdownUnder(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.walk(directory)) {
      return paths.filter(path -> path.toString().endsWith(".md")).sorted().toList();
    }
  }

  private static List<Path> directories(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.list(directory)) {
      return paths.filter(Files::isDirectory).sorted().toList();
    }
  }

  static String read(Path path) throws IOException {
    return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
  }
}
