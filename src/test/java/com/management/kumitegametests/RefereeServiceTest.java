package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.management.referee.data.RefereeDocument;
import com.management.referee.data.RefereeRepository;
import com.management.referee.domain.Referee;
import com.management.referee.domain.RefereeChangedException;
import com.management.referee.domain.RefereeNotFoundException;
import com.management.referee.service.RefereeService;
import com.management.testsupport.TestClock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class RefereeServiceTest {

  private final RefereeRepository repository = mock(RefereeRepository.class);
  private final RefereeService service = new RefereeService(repository);

  private static RefereeDocument stored(long version, String name) {
    return new RefereeDocument(
        "referee-1",
        version,
        RefereeDocument.CURRENT_SCHEMA_VERSION,
        name,
        TestClock.NOW,
        TestClock.NOW);
  }

  /** An absolute change: the loser hears what won, and the save is not attempted again. */
  @Test
  void rename_whenAnotherWriteWins_answersTheCurrentRefereeWithoutRetrying() {
    given(repository.findById("referee-1"))
        .willReturn(Optional.of(stored(1, "Sato")), Optional.of(stored(2, "Suzuki")));
    given(repository.save(any(RefereeDocument.class)))
        .willThrow(new OptimisticLockingFailureException("stale version"));

    assertThatThrownBy(() -> service.rename("referee-1", "Tanaka"))
        .isInstanceOfSatisfying(
            RefereeChangedException.class,
            changed -> assertThat(changed.current()).isEqualTo(new Referee("referee-1", "Suzuki")));
    then(repository).should().save(any(RefereeDocument.class));
  }

  @Test
  void rename_savesAgainstTheVersionItRead() {
    given(repository.findById("referee-1")).willReturn(Optional.of(stored(3, "Sato")));
    given(repository.save(any(RefereeDocument.class))).willAnswer(call -> call.getArgument(0));

    assertThat(service.rename("referee-1", "Tanaka")).isEqualTo(new Referee("referee-1", "Tanaka"));
    then(repository).should().save(argThat(document -> document.getVersion() == 3));
  }

  @Test
  void rename_whenDeletedInBetween_isNotFound() {
    given(repository.findById("referee-1"))
        .willReturn(Optional.of(stored(1, "Sato")), Optional.empty());
    given(repository.save(any(RefereeDocument.class)))
        .willThrow(new OptimisticLockingFailureException("gone"));

    assertThatThrownBy(() -> service.rename("referee-1", "Tanaka"))
        .isInstanceOf(RefereeNotFoundException.class);
  }

  @Test
  void delete_ofNobody_isNotFoundAndDeletesNothing() {
    given(repository.existsById("nobody")).willReturn(false);

    assertThatThrownBy(() -> service.delete("nobody")).isInstanceOf(RefereeNotFoundException.class);
    then(repository).should(never()).deleteById(anyString());
  }
}
