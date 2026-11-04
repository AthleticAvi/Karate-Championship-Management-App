package com.management.docs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommitMessageCheckTest {

  @Test
  void problems_whenMessageFollowsTheRules_findsNothing() {
    String message = "Add login form\n\nWhy it exists.\n\nCo-Authored-By: A <a@example.invalid>\n";

    assertThat(CommitMessageCheck.problems(message)).isEmpty();
  }

  @Test
  void problems_whenSubjectHasNoActionVerb_reportsIt() {
    assertThat(CommitMessageCheck.problems("feat: add login form"))
        .singleElement()
        .asString()
        .startsWith("start with an action verb");
  }

  @Test
  void problems_whenSubjectIsTooLong_reportsItsLength() {
    assertThat(CommitMessageCheck.problems("Add " + "x".repeat(69)))
        .containsExactly("subject is 73 characters, the limit is 72");
  }

  @Test
  void problems_whenSubjectIsExactlyTheLimit_findsNothing() {
    assertThat(CommitMessageCheck.problems("Add " + "x".repeat(68))).isEmpty();
  }

  @Test
  void problems_whenSubjectEndsWithFullStop_reportsIt() {
    assertThat(CommitMessageCheck.problems("Add login form."))
        .containsExactly("subject ends with a full stop");
  }

  @Test
  void problems_whenNoBlankLineFollowsSubject_reportsIt() {
    assertThat(CommitMessageCheck.problems("Add login form\nbody"))
        .containsExactly("leave a blank line after the subject");
  }

  @Test
  void problems_whenBodyIsTooLong_reportsIt() {
    assertThat(CommitMessageCheck.problems("Add login form\n\none\ntwo\nthree"))
        .containsExactly("body is 3 lines, the limit is 2");
  }

  @Test
  void problems_whenMessageIsEmpty_reportsIt() {
    assertThat(CommitMessageCheck.problems("# only a comment\n\n"))
        .containsExactly("message is empty");
  }

  @Test
  void problems_ignoresEverythingBelowTheScissorsLine() {
    String message =
        "Add login form\n\n# ------------------------ >8 ------------------------\none\ntwo\nthree";

    assertThat(CommitMessageCheck.problems(message)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Merge branch 'main'", "fixup! Add form", "Revert \"Add form\""})
  void problems_skipsSubjectsGitGenerates(String subject) {
    assertThat(CommitMessageCheck.problems(subject)).isEmpty();
  }
}
