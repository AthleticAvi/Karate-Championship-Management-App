package com.management.account.service;

import com.management.account.data.AccountRepository;
import com.management.account.domain.Account;
import com.management.account.domain.AccountNotFoundException;
import com.management.account.domain.AccountRole;
import com.management.account.domain.LastAdminException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes an account in one transaction, so at least one Admin always remains.
 *
 * <p><strong>A transaction alone would not hold this.</strong> Two Admins removing each other write
 * different documents, so both see two Admins, neither conflicts, and none is left. Writing to
 * every Admin first makes the two collide: one fails with a write conflict and runs again, and then
 * sees the other's removal.
 *
 * <p>A separate bean because the retry has to sit outside the transaction: see {@link
 * AccountService#delete}. Only that method calls this.
 */
@Service
public class AccountRemoval {

  private final AccountRepository accountRepository;

  public AccountRemoval(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  @Transactional
  public void remove(String username) {
    Account account =
        accountRepository
            .findById(username)
            .orElseThrow(() -> new AccountNotFoundException("No account has that username."));
    if (account.getRole() == AccountRole.ADMIN) {
      accountRepository.touchEveryAccountWithRole(AccountRole.ADMIN);
      if (accountRepository.countByRole(AccountRole.ADMIN) == 1) {
        throw new LastAdminException("The last Admin cannot be removed.");
      }
    }
    accountRepository.deleteById(username);
  }
}
