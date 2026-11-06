package com.management.models;

import com.management.enums.AccountRole;

/**
 * An account to create, as the business layer sees it. The gateway maps the request into this.
 *
 * @param passwordHash already hashed: see {@link Account}
 */
public record NewAccount(String username, String passwordHash, AccountRole role) {

  /** Leaves the hash out. */
  @Override
  public String toString() {
    return "NewAccount{username=" + username + ", role=" + role + "}";
  }
}
