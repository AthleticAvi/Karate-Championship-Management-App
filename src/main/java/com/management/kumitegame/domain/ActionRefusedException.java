package com.management.kumitegame.domain;

/**
 * An action refused because the data would contradict itself: the only kind of refusal there is.
 *
 * <p>Such as an entry id reused for another action, a CANCEL of something missing or already
 * cancelled, a settings change after the start, or one athlete on both sides. A rule breach that
 * officials may overrule is never refused: it is applied with a warning.
 */
public class ActionRefusedException extends RuntimeException {

  public ActionRefusedException(String message) {
    super(message);
  }
}
