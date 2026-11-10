package com.management.account.data;

import com.management.account.domain.Account;
import com.management.account.domain.AccountRole;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

public interface AccountRepository extends MongoRepository<Account, String> {

  boolean existsByRole(AccountRole role);

  long countByRole(AccountRole role);

  /**
   * Writes to every account with this role, so two transactions doing this collide. The field is
   * for this alone: nothing reads it, and the application has no property for it.
   */
  @Query("{ 'role': ?0 }")
  @Update("{ '$inc': { 'writeGuard': 1 } }")
  long touchEveryAccountWithRole(AccountRole role);
}
