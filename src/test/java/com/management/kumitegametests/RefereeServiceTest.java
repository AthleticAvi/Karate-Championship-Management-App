package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.management.referee.data.RefereeDocument;
import com.management.referee.data.RefereeRepository;
import com.management.referee.domain.Referee;
import com.management.referee.domain.RefereeChangedException;
import com.management.referee.domain.RefereeInUseException;
import com.management.referee.domain.RefereeNotFoundException;
import com.management.referee.service.RefereeReferences;
import com.management.referee.service.RefereeService;
import com.management.testsupport.TestClock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class RefereeServiceTest {

  private final RefereeRepository repository = mock(RefereeRepository.class);
  private final RefereeReferences references = mock(RefereeReferences.class);
  private final RefereeService service = new RefereeService(repository, references);

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
        .willReturn(Optional.of(stored(1, "Sato")))
        .willReturn(Optional.of(stored(2, "Suzuki")));
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
        .willReturn(Optional.of(stored(1, "Sato")))
        .willReturn(Optional.empty());
    given(repository.save(any(RefereeDocument.class)))
        .willThrow(new OptimisticLockingFailureException("gone"));

    assertThatThrownBy(() -> service.rename("referee-1", "Tanaka"))
        .isInstanceOf(RefereeNotFoundException.class);
  }

  @Test
  void delete_ofNobody_isNotFound() {
    given(repository.removeById("nobody")).willReturn(0L);

    assertThatThrownBy(() -> service.delete("nobody")).isInstanceOf(RefereeNotFoundException.class);
  }

  /** Deleting it would leave a game naming nobody, so nothing is deleted. */
  @Test
  void delete_whileGamesNameTheReferee_isRefused() {
    given(references.isRefereeReferred("referee-1")).willReturn(true);

    assertThatThrownBy(() -> service.delete("referee-1")).isInstanceOf(RefereeInUseException.class);
    then(repository).should(never()).removeById(any());
  }

  @Test
  void delete_ofRefereeInNoGame_deletesIt() {
    given(repository.removeById("referee-1")).willReturn(1L);

    service.delete("referee-1");

    then(repository).should().removeById("referee-1");
  }

  @Test
  void rename_toTheStoredName_writesNothing() {
    given(repository.findById("referee-1")).willReturn(Optional.of(stored(3, "Sato")));

    assertThat(service.rename("referee-1", "Sato")).isEqualTo(new Referee("referee-1", "Sato"));
    then(repository).should(never()).save(any(RefereeDocument.class));
  }
}
