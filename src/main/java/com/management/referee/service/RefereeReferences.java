package com.management.referee.service;

/**
 * Whether anything refers to a referee, so deleting it would leave a dangling id.
 *
 * <p>Owned here and implemented by the feature that refers to referees, because that feature may
 * use this one and never the reverse: the dependency is inverted rather than reversed.
 */
public interface RefereeReferences {

  boolean isRefereeReferred(String refereeId);
}
