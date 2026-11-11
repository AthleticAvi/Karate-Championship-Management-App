package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.management.athlete.data.AthleteDocument;
import com.management.athlete.data.AthleteRepository;
import com.management.athlete.domain.Athlete;
import com.management.athlete.domain.AthleteChangedException;
import com.management.athlete.domain.AthleteInUseException;
import com.management.athlete.domain.AthleteNotFoundException;
import com.management.athlete.service.AthleteReferences;
import com.management.athlete.service.AthleteService;
import com.management.testsupport.TestClock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class AthleteServiceTest {

  private final AthleteRepository repository = mock(AthleteRepository.class);
  private final AthleteReferences references = mock(AthleteReferences.class);
  private final AthleteService service = new AthleteService(repository, references);

  private static AthleteDocument stored(long version, String name) {
    return new AthleteDocument(
        "athlete-1",
        version,
        AthleteDocument.CURRENT_SCHEMA_VERSION,
        name,
        61.5,
        TestClock.NOW,
        TestClock.NOW);
  }

  /** Deleting it would leave a game naming nobody, so nothing is deleted. */
  @Test
  void delete_whileGamesNameTheAthlete_isRefused() {
    given(references.isAthleteReferred("athlete-1")).willReturn(true);

    assertThatThrownBy(() -> service.delete("athlete-1")).isInstanceOf(AthleteInUseException.class);
    then(repository).should(never()).removeById(any());
  }

  @Test
  void delete_ofAnAthleteInNoGame_deletesIt() {
    given(repository.removeById("athlete-1")).willReturn(1L);

    service.delete("athlete-1");

    then(repository).should().removeById("athlete-1");
  }

  @Test
  void delete_ofNobody_isNotFound() {
    assertThatThrownBy(() -> service.delete("nobody")).isInstanceOf(AthleteNotFoundException.class);
  }

  /** An absolute change: the loser hears what won, and the save is not attempted again. */
  @Test
  void update_whenAnotherWriteWins_answersTheCurrentAthleteWithoutRetrying() {
    given(repository.findById("athlete-1"))
        .willReturn(Optional.of(stored(1, "Kenji")))
        .willReturn(Optional.of(stored(2, "Kenta")));
    given(repository.save(any(AthleteDocument.class)))
        .willThrow(new OptimisticLockingFailureException("stale version"));

    assertThatThrownBy(() -> service.update("athlete-1", "Ken", 61.5))
        .isInstanceOfSatisfying(
            AthleteChangedException.class,
            changed ->
                assertThat(changed.current()).isEqualTo(new Athlete("athlete-1", "Kenta", 61.5)));
    then(repository).should().save(any(AthleteDocument.class));
  }

  @Test
  void update_toTheStoredValues_writesNothing() {
    given(repository.findById("athlete-1")).willReturn(Optional.of(stored(3, "Kenji")));

    assertThat(service.update("athlete-1", "Kenji", 61.5))
        .isEqualTo(new Athlete("athlete-1", "Kenji", 61.5));
    then(repository).should(never()).save(any(AthleteDocument.class));
  }
}
