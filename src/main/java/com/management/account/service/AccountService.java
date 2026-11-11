package com.management.account.service;

import com.management.account.data.AccountRepository;
import com.management.account.domain.Account;
import com.management.account.domain.AccountAlreadyExistsException;
import com.management.account.domain.AccountRole;
import com.management.account.domain.NewAccount;
import com.management.config.TransientTransactionRetry;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * The account aggregate. A leaf service, like {@code AthleteService}.
 *
 * <p>Passwords arrive here already hashed: see {@link Account}.
 */
@Service
public class AccountService {

  private final AccountRepository accountRepository;
  private final AccountRemoval accountRemoval;

  public AccountService(AccountRepository accountRepository, AccountRemoval accountRemoval) {
    this.accountRepository = accountRepository;
    this.accountRemoval = accountRemoval;
  }

  public Optional<Account> find(String username) {
    return accountRepository.findById(username);
  }

  public boolean hasAdmin() {
    return accountRepository.existsByRole(AccountRole.ADMIN);
  }

  /** One insert on the username, so the engine refuses a second one on any instance. */
  public Account create(NewAccount account) {
    try {
      return accountRepository.insert(
          new Account(account.username(), account.passwordHash(), account.role()));
    } catch (DuplicateKeyException e) {
      throw new AccountAlreadyExistsException("An account already has that username.", e);
    }
  }

  /**
   * Removes an account, unless it is the last Admin.
   *
   * <p>Retried here, outside the transaction, because the framework retries nothing by itself.
   * Sized like the append retries in {@code KumiteGameWriter}. Running out of attempts answers 409.
   */
  @Retryable(
      predicate = TransientTransactionRetry.class,
      maxRetries = 9,
      delay = 20,
      jitter = 20,
      multiplier = 1.5,
      maxDelay = 200)
  public void delete(String username) {
    accountRemoval.remove(username);
  }

  /**
   * Inserts the first Admin. Two instances starting together both try: the username is the document
   * id, so one insert loses, and it stops only if no Admin came of it.
   */
  public void createFirstAdmin(String username, String passwordHash) {
    try {
      accountRepository.insert(new Account(username, passwordHash, AccountRole.ADMIN));
    } catch (DuplicateKeyException e) {
      if (!hasAdmin()) {
        throw new IllegalStateException(
            "ADMIN_USERNAME already names an account that is not an Admin. Set another one.", e);
      }
    }
  }
}
