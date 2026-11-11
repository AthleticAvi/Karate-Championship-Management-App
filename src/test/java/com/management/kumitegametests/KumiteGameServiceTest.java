package com.management.kumitegametests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.management.athlete.domain.Athlete;
import com.management.athlete.service.AthleteService;
import com.management.config.KumiteGameProperties;
import com.management.kumitegame.data.KumiteGameDocument;
import com.management.kumitegame.data.KumiteGameDocumentMapper;
import com.management.kumitegame.data.KumiteGameRepository;
import com.management.kumitegame.domain.ActingRole;
import com.management.kumitegame.domain.AssignmentKind;
import com.management.kumitegame.domain.ClockActionKind;
import com.management.kumitegame.domain.GameChangedException;
import com.management.kumitegame.domain.GameFormat;
import com.management.kumitegame.domain.GameStatus;
import com.management.kumitegame.domain.InvalidGameRequestException;
import com.management.kumitegame.domain.KumiteGame;
import com.management.kumitegame.domain.NewAssignment;
import com.management.kumitegame.domain.NewClockAction;
import com.management.kumitegame.domain.NewGame;
import com.management.kumitegame.domain.NewResult;
import com.management.kumitegame.domain.NewScore;
import com.management.kumitegame.domain.NewStatusChange;
import com.management.kumitegame.domain.Outcome;
import com.management.kumitegame.domain.PanelRole;
import com.management.kumitegame.domain.Recording;
import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.ScoreLevel;
import com.management.kumitegame.domain.ScoreSource;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.StatusChangeKind;
import com.management.kumitegame.domain.Timer;
import com.management.kumitegame.service.KumiteGameService;
import com.management.kumitegame.service.KumiteGameWriter;
import com.management.referee.domain.Referee;
import com.management.referee.service.RefereeService;
import com.management.testsupport.FakeRepositories;
import com.management.testsupport.InMemoryMongo;
import com.management.testsupport.TestClock;
import com.management.testsupport.TestGames;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * The game service over an in-memory store: the settings snapshot, reads before writes, and an
 * absolute action that loses a race.
 */
class KumiteGameServiceTest {

  private static final Map<String, Athlete> ATHLETES =
      Map.of(
          TestGames.AKA_ATHLETE, new Athlete(TestGames.AKA_ATHLETE, "Kenji", null),
          TestGames.AO_ATHLETE, new Athlete(TestGames.AO_ATHLETE, "Haruto", null));
  private static final Map<String, Referee> REFEREES =
      Map.of(TestGames.REFEREE, new Referee(TestGames.REFEREE, "Sato"));

  private final InMemoryMongo storage = new InMemoryMongo();
  private final KumiteGameRepository repository = FakeRepositories.kumiteGames(storage);
  private final AthleteService athletes = mock(AthleteService.class);
  private final RefereeService referees = mock(RefereeService.class);

  @BeforeEach
  void knownAthletesAndReferees() {
    given(athletes.findAll(anyCollection()))
        .willAnswer(call -> existing(call.getArgument(0), ATHLETES));
    given(referees.findAll(anyCollection()))
        .willAnswer(call -> existing(call.getArgument(0), REFEREES));
  }

  private static <T> Map<String, T> existing(Collection<String> ids, Map<String, T> stored) {
    return ids.stream()
        .filter(stored::containsKey)
        .collect(Collectors.toMap(Function.identity(), stored::get));
  }

  private static KumiteGameProperties properties(Duration duration, int ipponPoints) {
    return new KumiteGameProperties(
        new KumiteGameProperties.Rules(
            duration,
            List.of(Duration.ofSeconds(90), Duration.ofMinutes(2), Duration.ofMinutes(3)),
            Map.of(ScoreLevel.IPPON, ipponPoints, ScoreLevel.WAZA_ARI, 2, ScoreLevel.YUKO, 1),
            8,
            Duration.ofSeconds(15),
            Duration.ofMillis(1500),
            3,
            4,
            List.of(Duration.ofSeconds(10), Duration.ofSeconds(20), Duration.ofSeconds(30)),
            Duration.ofMinutes(3),
            Duration.ofSeconds(1),
            "WKF 2026.01"),
        Map.of(Side.AKA, "red", Side.AO, "blue"));
  }

  private KumiteGameService service(KumiteGameProperties properties) {
    return new KumiteGameService(
        repository,
        new KumiteGameWriter(repository),
        athletes,
        referees,
        properties,
        TestClock.FIXED);
  }

  private final KumiteGameService service = service(properties(Duration.ofMinutes(2), 3));

  private String created() {
    return service
        .create(TestGames.newGame(GameFormat.ELIMINATION, false, false), TestGames.SCOREKEEPER)
        .game()
        .game()
        .id();
  }

  /** A configuration change reaches new games only: one already created keeps its copy. */
  @Test
  void gameKeepsItsSnapshot_whenConfigurationChangesAfterCreation() {
    String before = created();

    KumiteGameService redeployed = service(properties(Duration.ofSeconds(90), 5));
    String after =
        redeployed
            .create(TestGames.newGame(GameFormat.ELIMINATION, false, false), "scorekeeper")
            .game()
            .game()
            .id();

    KumiteGame kept = redeployed.get(before).game();
    assertThat(kept.settings().durationMs()).isEqualTo(120_000);
    assertThat(kept.settings().pointsFor(ScoreLevel.IPPON)).isEqualTo(3);
    assertThat(redeployed.get(after).game().settings().durationMs()).isEqualTo(90_000);
    assertThat(redeployed.get(after).game().settings().pointsFor(ScoreLevel.IPPON)).isEqualTo(5);
  }

  @Test
  void create_namesTheAthletesAndReferees_lookedUpWhenRead() {
    Recording recording =
        service.create(TestGames.newGame(GameFormat.ELIMINATION, false, false), "scorekeeper");

    assertThat(recording.game().athletes()).isEqualTo(ATHLETES);
    assertThat(recording.game().referees()).isEqualTo(REFEREES);
    assertThat(recording.game().serverTime()).isEqualTo(TestClock.NOW);
    assertThat(recording.raisedWarnings()).isEmpty();
  }

  @Test
  void create_withAnAthleteThatDoesNotExist_writesNothing() {
    assertThatThrownBy(() ->
            service.create(
                new NewGame(
                    GameFormat.ELIMINATION,
                    false,
                    false,
                    "nobody",
                    TestGames.AO_ATHLETE,
                    List.of()),
                "scorekeeper"))
        .isInstanceOf(InvalidGameRequestException.class)
        .hasMessageContaining("nobody");
    assertThat(storage.count(KumiteGameDocument.class)).isZero();
  }

  @Test
  void assignment_ofMissingReferee_writesNothing() {
    String gameId = created();
    clearInvocations(repository);

    assertThatThrownBy(() ->
            service.recordAssignment(
                gameId,
                new NewAssignment(
                    "a1", AssignmentKind.PANEL_ADD, null, null, "nobody", PanelRole.JUDGE),
                "scorekeeper"))
        .isInstanceOf(InvalidGameRequestException.class);
    verify(repository, never()).save(any(KumiteGameDocument.class));
  }

  /** SET_TIME sets a value, so it takes the absolute path: a lost race is not retried. */
  @Test
  void setTime_thatLosesRace_answersTheCurrentGame_andOtherClockActionsAppend() {
    String gameId = created();
    service.recordClockAction(
        gameId,
        new NewClockAction("c1", Timer.MAIN, null, ClockActionKind.START, 120_000, null, null),
        "scorekeeper");
    willThrow(new OptimisticLockingFailureException("stale version"))
        .given(repository)
        .save(any(KumiteGameDocument.class));

    assertThatThrownBy(() ->
            service.recordClockAction(
                gameId,
                new NewClockAction(
                    "c2", Timer.MAIN, null, ClockActionKind.SET_TIME, 119_000, 60_000L, null),
                "scorekeeper"))
        .isInstanceOfSatisfying(
            GameChangedException.class,
            changed -> assertThat(changed.current().game().clockActions()).hasSize(1));
  }

  @Test
  void athleteAssignment_ofAthleteThatDoesNotExist_writesNothing() {
    String gameId = created();
    clearInvocations(repository);

    assertThatThrownBy(() ->
            service.recordAssignment(
                gameId,
                new NewAssignment("a1", AssignmentKind.ATHLETE, Side.AKA, "nobody", null, null),
                "scorekeeper"))
        .isInstanceOf(InvalidGameRequestException.class);
    verify(repository, never()).save(any(KumiteGameDocument.class));
  }

  @Test
  void retriedEntry_writesNothing() {
    String gameId = created();
    NewScore yuko = new NewScore("s1", Side.AKA, ScoreLevel.YUKO, ScoreSource.JUDGES, null, null);
    service.recordScore(gameId, yuko, "scorekeeper");
    clearInvocations(repository);

    Recording retry = service.recordScore(gameId, yuko, "scorekeeper");

    verify(repository, never()).save(any(KumiteGameDocument.class));
    assertThat(retry.game().game().scores(Side.AKA)).hasSize(1);
  }

  /**
   * Another write lands between this action's read and its save. A result sets a value, so it is
   * not retried: the caller gets the game as it now stands, with the other write in it.
   */
  @Test
  void absoluteAction_thatLosesRace_answersTheCurrentGameAndIsNotRetried() {
    String gameId = created();
    willAnswer(
            call -> {
              KumiteGameDocument stored = storage.findById(KumiteGameDocument.class, gameId).get();
              KumiteGame other = KumiteGameDocumentMapper.toDomain(stored);
              other.recordScore(
                  new NewScore("other", Side.AO, ScoreLevel.IPPON, ScoreSource.JUDGES, null, null),
                  "another-scorekeeper",
                  TestClock.NOW);
              storage.save(KumiteGameDocumentMapper.toDocument(other, stored));
              throw new OptimisticLockingFailureException("stale version");
            })
        .given(repository)
        .save(any(KumiteGameDocument.class));

    assertThatThrownBy(() ->
            service.recordResult(
                gameId,
                new NewResult("r1", Outcome.WIN, Side.AKA, ResultReason.POINTS, null, false),
                "scorekeeper"))
        .isInstanceOfSatisfying(
            GameChangedException.class,
            changed -> {
              assertThat(changed.current().game().scores(Side.AO)).hasSize(1);
              assertThat(changed.current().game().results()).isEmpty();
            });
    verify(repository).save(any(KumiteGameDocument.class));
  }

  /** Approve, reopen and void take the same path: a lost race answers the game, untried again. */
  @Test
  void statusChange_thatLosesRace_answersTheCurrentGameAndIsNotRetried() {
    String gameId = created();
    willAnswer(
            call -> {
              KumiteGameDocument stored = storage.findById(KumiteGameDocument.class, gameId).get();
              KumiteGame other = KumiteGameDocumentMapper.toDomain(stored);
              other.recordResult(
                  new NewResult("other", Outcome.WIN, Side.AO, ResultReason.KIKEN, null, false),
                  "another-scorekeeper",
                  TestClock.NOW);
              storage.save(KumiteGameDocumentMapper.toDocument(other, stored));
              throw new OptimisticLockingFailureException("stale version");
            })
        .given(repository)
        .save(any(KumiteGameDocument.class));

    assertThatThrownBy(() ->
            service.recordStatusChange(
                gameId,
                new NewStatusChange("v1", StatusChangeKind.VOID, "never fought"),
                ActingRole.ADMIN,
                "admin"))
        .isInstanceOfSatisfying(
            GameChangedException.class,
            changed -> {
              assertThat(changed.current().state().status()).isEqualTo(GameStatus.DECIDED);
              assertThat(changed.current().game().statusChanges()).isEmpty();
            });
    verify(repository).save(any(KumiteGameDocument.class));
  }
}
