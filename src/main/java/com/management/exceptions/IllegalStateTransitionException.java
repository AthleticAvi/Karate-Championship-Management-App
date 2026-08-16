package com.management.exceptions;

/**
 * A lifecycle operation was asked of a match whose state does not allow it — starting a finished
 * match, pausing one that is not running.
 *
 * <p>Maps to HTTP 409 Conflict: the request was well formed and the match exists, but the resource
 * is not in a state that permits the operation. The message names the current state and the
 * attempted transition, and is written by this project, so the handler echoes it to the caller.
 */
public class IllegalStateTransitionException extends RuntimeException {
  public IllegalStateTransitionException(String message) {
    super(message);
  }
}
