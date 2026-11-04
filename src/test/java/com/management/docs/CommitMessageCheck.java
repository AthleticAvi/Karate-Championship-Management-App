package com.management.docs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks a commit message against {@code context/workflow/git/commits.md}.
 *
 * <p>Run from the repository root: {@code java
 * src/test/java/com/management/docs/CommitMessageCheck.java [--reject-autosquash] MESSAGE_FILE}.
 * The {@code commit-msg} hook and CI both call it. It stays one file with only JDK imports, because
 * the source launcher can run nothing else.
 */
public final class CommitMessageCheck {

  private static final int MAX_SUBJECT = 72;
  private static final int MAX_BODY_LINES = 2;
  private static final String SCISSORS_PREFIX = "# ------------------------ >8";
  private static final List<String> TRAILERS = List.of("co-authored-by:", "signed-off-by:");
  private static final String REJECT_AUTOSQUASH = "--reject-autosquash";
  private static final List<String> AUTOSQUASH_SUBJECTS = List.of("fixup! ", "squash! ", "amend! ");
  private static final List<String> GENERATED_SUBJECTS =
      List.of(
          "Merge branch ",
          "Merge pull request ",
          "Merge remote-tracking branch ",
          "Merge tag ",
          "Revert \"");
  private static final Set<String> VERBS =
      new TreeSet<>(
          List.of(
              "Add",
              "Align",
              "Append",
              "Bump",
              "Capture",
              "Change",
              "Clean",
              "Convert",
              "Correct",
              "Cover",
              "Document",
              "Drop",
              "Enforce",
              "Extract",
              "Fix",
              "Freeze",
              "Harden",
              "Improve",
              "Merge",
              "Migrate",
              "Move",
              "Pin",
              "Port",
              "Reduce",
              "Refactor",
              "Remove",
              "Rename",
              "Replace",
              "Restructure",
              "Revert",
              "Rewrite",
              "Run",
              "Set",
              "Split",
              "Test",
              "Tune",
              "Update"));

  private CommitMessageCheck() {
    throw new UnsupportedOperationException("static entry point");
  }

  /** Checks the message file git passes to the hook. Exits non-zero if it breaks a rule. */
  public static void main(String[] args) throws IOException {
    String message = Files.readString(Path.of(args[args.length - 1]), StandardCharsets.UTF_8);
    List<String> problems = problems(message, List.of(args).contains(REJECT_AUTOSQUASH));
    problems.forEach(System.out::println);
    System.exit(problems.isEmpty() ? 0 : 1);
  }

  /** Returns every rule the message breaks, allowing autosquash commits as a local hook must. */
  static List<String> problems(String message) {
    return problems(message, false);
  }

  /** Returns every rule the message breaks. A PR must not carry autosquash commits to main. */
  static List<String> problems(String message, boolean rejectAutosquash) {
    List<String> lines = messageLines(message);
    if (lines.isEmpty()) {
      return List.of("message is empty");
    }
    String subject = lines.get(0);
    if (AUTOSQUASH_SUBJECTS.stream().anyMatch(subject::startsWith)) {
      return rejectAutosquash ? List.of("squash this commit before opening the PR") : List.of();
    }
    if (GENERATED_SUBJECTS.stream().anyMatch(subject::startsWith)) {
      return List.of();
    }
    List<String> problems = new ArrayList<>();
    if (!VERBS.contains(subject.split("\\s+", 2)[0])) {
      problems.add("start with an action verb: " + String.join(", ", VERBS));
    }
    if (subject.length() > MAX_SUBJECT) {
      problems.add("subject is " + subject.length() + " characters, the limit is " + MAX_SUBJECT);
    }
    if (subject.endsWith(".")) {
      problems.add("subject ends with a full stop");
    }
    if (lines.size() > 1 && !lines.get(1).isEmpty()) {
      problems.add("leave a blank line after the subject");
    }
    long body = lines.stream().skip(1).filter(CommitMessageCheck::isBodyLine).count();
    if (body > MAX_BODY_LINES) {
      problems.add("body is " + body + " lines, the limit is " + MAX_BODY_LINES);
    }
    return problems;
  }

  private static boolean isBodyLine(String line) {
    String lower = line.toLowerCase(Locale.ROOT);
    return !line.isEmpty() && TRAILERS.stream().noneMatch(lower::startsWith);
  }

  /** Returns the message lines without git comments and outer blank lines. */
  private static List<String> messageLines(String message) {
    List<String> lines = new ArrayList<>();
    for (String line : Arrays.asList(message.replace("\r\n", "\n").split("\n", -1))) {
      if (line.startsWith(SCISSORS_PREFIX)) {
        break;
      }
      if (!line.startsWith("#")) {
        lines.add(line.stripTrailing());
      }
    }
    while (!lines.isEmpty() && lines.get(0).isEmpty()) {
      lines.remove(0);
    }
    while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
      lines.remove(lines.size() - 1);
    }
    return lines;
  }
}
