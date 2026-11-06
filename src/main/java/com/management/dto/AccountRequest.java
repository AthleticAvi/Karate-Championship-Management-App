package com.management.dto;

import com.management.enums.AccountRole;
import com.management.models.Account;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An account to create.
 *
 * <p>The size limit on the password is the hash's own: it takes 72 bytes. A password within this
 * limit that is longer than that in bytes is refused when it is hashed.
 *
 * @param username who signs in. It cannot be changed afterwards
 * @param password the password in clear, hashed before it goes any further
 * @param role what the account may do
 */
public record AccountRequest(
    @NotNull(message = "an account needs a username")
        @Pattern(
            regexp = Account.USERNAME_PATTERN,
            message = "must be 1 to 64 letters, digits, dots, underscores or hyphens")
        String username,
    @Schema(format = "password")
        @NotBlank(message = "an account needs a password")
        @Size(max = 72, message = "must be at most 72 characters")
        String password,
    @NotNull(message = "an account needs a role") AccountRole role) {

  /** Leaves the password out: a failed validation can print this record. */
  @Override
  public String toString() {
    return "AccountRequest{username=" + username + ", role=" + role + "}";
  }
}
