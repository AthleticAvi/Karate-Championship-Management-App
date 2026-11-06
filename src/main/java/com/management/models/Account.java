package com.management.models;

import com.management.enums.AccountRole;

/**
 * Someone who may sign in: a username, a password hash and a role.
 *
 * <p>The username is the document id, so the engine keeps it unique on every instance with no
 * index. The field is named {@code id} because that is what the mapping stores as the document id
 * with no annotation, which keeps this type out of the frozen persistence-mapping rule. An account
 * is only ever inserted or deleted, so it has no version field.
 *
 * <p>The hash arrives already made: hashing belongs to the gateways, because the encoder is a
 * Spring Security type. {@link #toString()} leaves it out.
 */
public final class Account {

  /** Nothing here can break a URL or the colon that ends a username in Basic credentials. */
  public static final String USERNAME_PATTERN = "[A-Za-z0-9._-]{1,64}";

  private final String id;
  private final String passwordHash;
  private final AccountRole role;

  public Account(String id, String passwordHash, AccountRole role) {
    this.id = id;
    this.passwordHash = passwordHash;
    this.role = role;
  }

  public String getUsername() {
    return id;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public AccountRole getRole() {
    return role;
  }

  @Override
  public String toString() {
    return "Account{username=" + id + ", role=" + role + "}";
  }
}
