package com.management.security;

import com.management.account.domain.Account;
import com.management.account.service.AccountService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Lets the framework check credentials against the accounts in the datastore.
 *
 * <p>Read on every request, so a deleted account stops working on its next call.
 */
final class AccountUserDetailsService implements UserDetailsService {

  private final AccountService accounts;

  AccountUserDetailsService(AccountService accounts) {
    this.accounts = accounts;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    return accounts
        .find(username)
        .map(AccountUserDetailsService::toUserDetails)
        .orElseThrow(() -> new UsernameNotFoundException("No such account"));
  }

  private static UserDetails toUserDetails(Account account) {
    return User.withUsername(account.getUsername())
        .password(account.getPasswordHash())
        .roles(account.getRole().name())
        .build();
  }
}
