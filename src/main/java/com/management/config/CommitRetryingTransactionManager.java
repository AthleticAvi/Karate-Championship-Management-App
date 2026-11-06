package com.management.config;

import com.mongodb.MongoException;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Retries a commit whose result is unknown, and only the commit.
 *
 * <p>The framework ignores the error label, and running the whole method again could apply the
 * change twice. When the attempts run out the failure goes on, so it is a 500 and not a silent
 * success. See {@code [transactions]} in {@code
 * context/workflow/databases/mongodb/design/persistence.md}.
 */
class CommitRetryingTransactionManager extends MongoTransactionManager {

  private static final int COMMIT_ATTEMPTS = 3;

  CommitRetryingTransactionManager(MongoDatabaseFactory databaseFactory) {
    super(databaseFactory);
  }

  @Override
  protected void doCommit(MongoTransactionObject transaction) throws Exception {
    for (int attempt = 1; ; attempt++) {
      try {
        transaction.commitTransaction();
        return;
      } catch (MongoException e) {
        boolean unknown = e.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        if (!unknown || attempt == COMMIT_ATTEMPTS) {
          throw e;
        }
      }
    }
  }
}
