package com.management.mappers;

import com.management.dto.AccountRequest;
import com.management.dto.AccountResponse;
import com.management.models.Account;
import com.management.models.NewAccount;

/** Turns a request into a new account, and an account into what the API reports. */
public final class AccountMapper {

  private AccountMapper() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  public static NewAccount toNewAccount(AccountRequest request, String passwordHash) {
    return new NewAccount(request.username(), passwordHash, request.role());
  }

  public static AccountResponse toResponse(Account account) {
    return new AccountResponse(account.getUsername(), account.getRole());
  }
}
