package com.management.config;

import com.mongodb.MongoException;
import java.lang.reflect.Method;
import org.springframework.resilience.retry.MethodRetryPredicate;

/**
 * Retries a transaction the server says is safe to run again, such as one that lost a write
 * conflict.
 *
 * <p>Read from the error label, because the exception type says nothing: the mapping layer turns a
 * write conflict into a data integrity violation, the parent of a duplicate key. See {@code
 * [transactions]} in {@code context/workflow/databases/mongodb/design/persistence.md}. It lives
 * here so a driver type stays out of the services.
 */
public class TransientTransactionRetry implements MethodRetryPredicate {

  @Override
  public boolean shouldRetry(Method method, Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof MongoException mongo
          && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
        return true;
      }
    }
    return false;
  }
}
