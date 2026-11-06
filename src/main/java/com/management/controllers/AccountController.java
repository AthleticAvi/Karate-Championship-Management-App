package com.management.controllers;

import com.management.dto.AccountRequest;
import com.management.dto.AccountResponse;
import com.management.mappers.AccountMapper;
import com.management.services.AccountService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creates and removes accounts. There is no way to change a password: delete and create again.
 *
 * <p>The password is hashed here because the encoder is a Spring Security type, which stays out of
 * business code.
 */
@RestController
@RequestMapping(AccountController.PATH)
public class AccountController {

  static final String PATH = "/accounts";

  private final AccountService accountService;
  private final PasswordEncoder passwordEncoder;

  public AccountController(AccountService accountService, PasswordEncoder passwordEncoder) {
    this.accountService = accountService;
    this.passwordEncoder = passwordEncoder;
  }

  @ApiResponse(responseCode = "201", description = "Created")
  @ApiResponse(
      responseCode = "409",
      description = "Conflict",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(ref = "#/components/schemas/ProblemDetail")))
  @PostMapping
  public ResponseEntity<AccountResponse> createAccount(
      @Valid @RequestBody AccountRequest newAccount) {
    AccountResponse created =
        AccountMapper.toResponse(
            accountService.create(
                AccountMapper.toNewAccount(
                    newAccount, passwordEncoder.encode(newAccount.password()))));
    return ResponseEntity.status(HttpStatus.CREATED)
        .header("Location", ApiVersionConfiguration.PREFIX + PATH + "/" + created.username())
        .body(created);
  }

  @ApiResponse(responseCode = "204", description = "No Content")
  @DeleteMapping("/{username}")
  public ResponseEntity<Void> deleteAccount(@PathVariable String username) {
    accountService.delete(username);
    return ResponseEntity.noContent().build();
  }
}
