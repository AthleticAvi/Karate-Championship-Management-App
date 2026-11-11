package com.management.athlete.web;

import com.management.athlete.domain.Athlete;

/** Turns an athlete into the athlete the API reports. */
public final class AthleteMapper {

  private AthleteMapper() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  public static AthleteResponse toResponse(Athlete athlete) {
    return new AthleteResponse(athlete.id(), athlete.name(), athlete.weightKg());
  }
}
