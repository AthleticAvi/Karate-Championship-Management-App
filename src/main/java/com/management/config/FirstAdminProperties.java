package com.management.config;

import com.management.models.Account;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The first Admin, from {@code ADMIN_USERNAME} and {@code ADMIN_PASSWORD}, used only when no Admin
 * exists.
 *
 * <p>Neither has a default in any profile. Blank means not set: a placeholder with no value at all
 * would reach this record as its own text and become a password. The password carries no constraint
 * because a failed one prints its value, and {@link #toString()} leaves it out for the same reason.
 *
 * @param username the first Admin's username, blank when not set
 * @param password the first Admin's password, blank when not set
 * @param required whether starting with no Admin and no values is refused or only warned about
 */
@Validated
@ConfigurationProperties("accounts.first-admin")
public record FirstAdminProperties(
    @Pattern(
            regexp = "|" + Account.USERNAME_PATTERN,
            message = "must be 1 to 64 letters, digits, dots, underscores or hyphens")
        String username,
    String password,
    boolean required) {

  @Override
  public String toString() {
    return "FirstAdminProperties{username=" + username + ", required=" + required + "}";
  }
}
