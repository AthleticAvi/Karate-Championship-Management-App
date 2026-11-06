package com.management.services;

import com.management.enums.AccountRole;
import com.management.models.Account;
import com.management.repositories.AccountRepository;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * The account aggregate. A leaf service, like {@code PlayerService}.
 *
 * <p>Passwords arrive here already hashed: see {@link Account}.
 */
@Service
public class AccountService {

  private final AccountRepository accountRepository;

  public AccountService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  public Optional<Account> find(String username) {
    return accountRepository.findById(username);
  }

  public boolean hasAdmin() {
    return accountRepository.existsByRole(AccountRole.ADMIN);
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
