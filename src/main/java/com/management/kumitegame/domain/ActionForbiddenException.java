package com.management.kumitegame.domain;

/**
 * An action the signed-in account's role may not take on the game as it stands, such as a
 * Scorekeeper reopening an APPROVED game.
 *
 * <p>The role table cannot refuse it, because the same endpoint admits the action from a
 * Scorekeeper in another status. The game decides, and the answer is the same 403.
 */
public class ActionForbiddenException extends RuntimeException {

  public ActionForbiddenException(String message) {
    super(message);
  }
}
