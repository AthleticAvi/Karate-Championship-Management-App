package com.management.kumitegame.domain;

/**
 * The role the signed-in account acts under, passed in by the gateway as a plain value, the way
 * {@code recordedBy} is. An Admin may do everything a Scorekeeper may.
 *
 * <p>Its names are the account roles' names, which is how the gateway finds it.
 */
public enum ActingRole {
  SCOREKEEPER,
  ADMIN
}
