package com.management.referee.domain;

import com.management.domain.IdentityChangedException;

/** A rename lost to another write on the same referee. */
public class RefereeChangedException extends IdentityChangedException {

  public RefereeChangedException(Referee current, Throwable cause) {
    super("referee", "Referee " + current.id() + " was changed by another request", current, cause);
  }
}
