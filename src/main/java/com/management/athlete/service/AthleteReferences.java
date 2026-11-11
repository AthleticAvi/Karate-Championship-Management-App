package com.management.athlete.service;

/**
 * Whether anything refers to an athlete, so deleting it would leave a dangling id.
 *
 * <p>Owned here and implemented by the feature that refers to athletes, because that feature may
 * use this one and never the reverse: the dependency is inverted rather than reversed.
 */
public interface AthleteReferences {

  boolean isAthleteReferred(String athleteId);
}
