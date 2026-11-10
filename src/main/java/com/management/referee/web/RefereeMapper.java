package com.management.referee.web;

import com.management.referee.domain.Referee;

/** Turns a referee into the referee the API reports. */
public final class RefereeMapper {

  private RefereeMapper() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  public static RefereeResponse toResponse(Referee referee) {
    return new RefereeResponse(referee.id(), referee.name());
  }
}
