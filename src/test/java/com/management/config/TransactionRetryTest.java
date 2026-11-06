package com.management.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mongodb.MongoException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.MongoDatabaseFactory;

class TransactionRetryTest {

  private final RecordingManager manager = new RecordingManager();
  private final TransientTransactionRetry retry = new TransientTransactionRetry();

  private static MongoException labelled(String label) {
    MongoException failure = new MongoException("failed");
    failure.addLabel(label);
    return failure;
  }

  private static MongoException unknownResult() {
    return labelled(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
  }

  @Test
  void commit_whoseResultIsUnknown_isRetried() throws Exception {
    manager.failOnce(unknownResult());

    manager.commit();

    manager.verifyAttempts(2);
  }

  @Test
  void commit_whoseResultStaysUnknown_failsLoudly() {
    manager.failAlways(unknownResult());

    assertThatThrownBy(manager::commit).isInstanceOf(MongoException.class);

    manager.verifyAttempts(3);
  }

  @Test
  void commit_thatFailedForAnyOtherReason_isNotRetried() {
    manager.failAlways(new MongoException("failed"));

    assertThatThrownBy(manager::commit).isInstanceOf(MongoException.class);

    manager.verifyAttempts(1);
  }

  @Test
  void commit_thatSucceeds_runsOnce() throws Exception {
    manager.commit();

    manager.verifyAttempts(1);
  }

  /** The label is on the cause: the mapping layer wraps a write conflict. */
  @Test
  void transientFailure_isRetried() {
    Throwable wrapped =
        new DataIntegrityViolationException(
            "conflict", labelled(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL));

    assertThat(retry.shouldRetry(null, wrapped)).isTrue();
  }

  @Test
  void anythingElse_isNotRetried() {
    assertThat(retry.shouldRetry(null, new DuplicateKeyException("taken"))).isFalse();
    assertThat(retry.shouldRetry(null, new MongoException("failed"))).isFalse();
  }

  /** The transaction type is only visible to a subclass of the manager. */
  private static final class RecordingManager extends CommitRetryingTransactionManager {

    private final MongoTransactionObject transaction = mock(MongoTransactionObject.class);

    RecordingManager() {
      super(mock(MongoDatabaseFactory.class));
    }

    void failOnce(MongoException failure) {
      willThrow(failure).willAnswer(call -> null).given(transaction).commitTransaction();
    }

    void failAlways(MongoException failure) {
      willThrow(failure).given(transaction).commitTransaction();
    }

    void commit() throws Exception {
      doCommit(transaction);
    }

    void verifyAttempts(int attempts) {
      verify(transaction, times(attempts)).commitTransaction();
    }
  }
}
