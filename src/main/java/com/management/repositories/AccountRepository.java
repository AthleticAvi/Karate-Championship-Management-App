package com.management.repositories;

import com.management.enums.AccountRole;
import com.management.models.Account;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AccountRepository extends MongoRepository<Account, String> {

  boolean existsByRole(AccountRole role);
}
