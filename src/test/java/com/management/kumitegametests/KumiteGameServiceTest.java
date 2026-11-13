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
import com.management.athlete.service.VersionedAthlete;
import com.management.config.KumiteGameProperties;
import com.management.kumitegame.data.KumiteGameDocument;
import com.management.kumitegame.data.KumiteGameDocumentMapper;
import com.management.kumitegame.data.KumiteGameRepository;
import com.management.kumitegame.domain.ActingRole;
import com.management.kumitegame.domain.AssignmentKind;
import com.management.kumitegame.domain.ClockActionKind;
import com.management.kumitegame.domain.GameChangedException;
import com.management.kumitegame.domain.GameFormat;
import com.management.kumitegame.domain.GameRead;
import com.management.kumitegame.domain.GameStatus;
import com.management.kumitegame.domain.GameWarning;
import com.management.kumitegame.domain.InvalidGameRequestException;
import com.management.kumitegame.domain.KumiteGame;
import com.management.kumitegame.domain.NewAssignment;
import com.management.kumitegame.domain.NewClockAction;
import com.management.kumitegame.domain.NewGame;
import com.management.kumitegame.domain.NewResult;
import com.management.kumitegame.domain.NewScore;
import com.management.kumitegame.domain.NewSettings;
import com.management.kumitegame.domain.NewStatusChange;
import com.management.kumitegame.domain.Outcome;
import com.management.kumitegame.domain.PanelRole;
import com.management.kumitegame.domain.Recording;
import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.ResultRules;
import com.management.kumitegame.domain.Score;
import com.management.kumitegame.domain.ScoreLevel;
import com.management.kumitegame.domain.ScoreSource;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.StatusChangeKind;
import com.management.kumitegame.domain.SuggestedResult;
import com.management.kumitegame.domain.Timer;
import com.management.kumitegame.domain.Warning;
import com.management.kumitegame.service.KumiteGameService;
import com.management.kumitegame.service.KumiteGameWriter;
import com.management.kumitegame.service.VersionedGameRead;
import com.management.referee.domain.Referee;
import com.management.referee.service.RefereeService;
import com.management.referee.service.VersionedReferee;
import com.management.testsupport.FakeRepositories;
import com.management.testsupport.InMemoryMongo;
import com.management.testsupport.TestClock;
import com.management.testsupport.TestGames;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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

  private static final Athlete KENJI = new Athlete(TestGames.AKA_ATHLETE, "Kenji", null);
  private static final Referee SATO = new Referee(TestGames.REFEREE, "Sato");
  private static final Map<String, Athlete> ATHLETES =
      Map.of(
          TestGames.AKA_ATHLETE,
          KENJI,
          TestGames.AO_ATHLETE,
          new Athlete(TestGames.AO_ATHLETE, "Haruto", null));
  private static final Map<String, Referee> REFEREES = Map.of(TestGames.REFEREE, SATO);

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
    return properties(duration, ipponPoints, 8);
  }

  private static KumiteGameProperties properties(
      Duration duration, int ipponPoints, int leadToWin) {
    return new KumiteGameProperties(
        new KumiteGameProperties.Rules(
            duration,
            List.of(Duration.ofSeconds(90), Duration.ofMinutes(2), Duration.ofMinutes(3)),
            Map.of(ScoreLevel.IPPON, ipponPoints, ScoreLevel.WAZA_ARI, 2, ScoreLevel.YUKO, 1),
            leadToWin,
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
        new KumiteGameWriter(repository, TestGames.rules()),
        athletes,
        referees,
        properties,
        TestGames.rules(),
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

  /**
   * The suggestion reads the lead to win from the game's snapshot: a 6-point lead wins a game
   * created under a lead of 6, and not one created under 8, whatever configuration now says.
   */
  @Test
  void suggestion_readsTheLeadFromTheSnapshot_notFromConfiguration() {
    String underEight = created();
    KumiteGameService leadOfSix = service(properties(Duration.ofMinutes(2), 3, 6));
    String underSix =
        leadOfSix
            .create(TestGames.newGame(GameFormat.ELIMINATION, false, false), "scorekeeper")
            .game()
            .game()
            .id();
    for (String gameId : List.of(underEight, underSix)) {
      leadOfSix.recordClockAction(
          gameId,
          new NewClockAction("c", Timer.MAIN, null, ClockActionKind.START, 120_000, null, null),
          null,
          TestGames.SCOREKEEPER);
      for (String id : List.of("s1", "s2")) {
        leadOfSix.recordScore(
            gameId,
            new NewScore(id, Side.AKA, ScoreLevel.IPPON, ScoreSource.JUDGES, null, null),
            TestGames.SCOREKEEPER);
      }
    }

    // Read under a configuration of 6, and of 8: each game keeps the lead it was created with.
    for (KumiteGameService reader : List.of(leadOfSix, service)) {
      assertThat(reader.get(underEight).state().side(Side.AKA).total()).isEqualTo(6);
      assertThat(reader.get(underEight).state().suggested()).isNull();
      assertThat(reader.get(underSix).state().suggested())
          .isEqualTo(SuggestedResult.win(Side.AKA, ResultReason.LEAD));
    }
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

  /**
   * Each version comes from the record it describes: the game's from its document, each athlete's
   * and referee's from the same lookup as its name. One that no longer exists is absent.
   */
  @Test
  void getVersioned_readsTheGameWithTheVersionOfEveryRecordItShows() {
    String gameId = created();
    given(athletes.findAllVersioned(anyCollection()))
        .willReturn(Map.of(TestGames.AKA_ATHLETE, new VersionedAthlete(KENJI, 4)));
    given(referees.findAllVersioned(anyCollection()))
        .willReturn(Map.of(TestGames.REFEREE, new VersionedReferee(SATO, 2)));
    long stored = storage.findById(KumiteGameDocument.class, gameId).orElseThrow().getVersion();

    VersionedGameRead versioned = service.getVersioned(gameId);

    assertThat(versioned.gameVersion()).isEqualTo(stored);
    assertThat(versioned.athleteVersions()).containsExactly(Map.entry(TestGames.AKA_ATHLETE, 4L));
    assertThat(versioned.refereeVersions()).containsExactly(Map.entry(TestGames.REFEREE, 2L));
    assertThat(versioned.read().athletes())
        .containsOnlyKeys(TestGames.AKA_ATHLETE)
        .containsEntry(TestGames.AKA_ATHLETE, KENJI);
    assertThat(versioned.read().referees()).isEqualTo(REFEREES);
    assertThat(versioned.read().game().id()).isEqualTo(gameId);
    assertThat(versioned.read().serverTime()).isEqualTo(TestClock.NOW);
    verify(athletes).findAllVersioned(Set.of(TestGames.AKA_ATHLETE, TestGames.AO_ATHLETE));
    verify(referees).findAllVersioned(Set.of(TestGames.REFEREE));
  }

  /**
   * The versions are read without folding the history: a caller answering 304 needs nothing more.
   * The fold, when asked for, reads nothing again, so the read matches the versions. Folds are
   * counted through the result rules, which every fold of a game that is not VOIDED asks.
   */
  @Test
  void getVersioned_foldsOnlyWhenTheReadIsAskedFor() {
    final String gameId = created();
    clearInvocations(athletes, referees);
    given(athletes.findAllVersioned(anyCollection())).willReturn(Map.of());
    given(referees.findAllVersioned(anyCollection())).willReturn(Map.of());
    AtomicInteger folds = new AtomicInteger();
    ResultRules counted =
        standing -> {
          folds.incrementAndGet();
          return TestGames.rules().suggest(standing);
        };
    final KumiteGameService watched =
        new KumiteGameService(
            repository,
            new KumiteGameWriter(repository, counted),
            athletes,
            referees,
            properties(Duration.ofMinutes(2), 3),
            counted,
            TestClock.FIXED);

    VersionedGameRead versioned = watched.getVersioned(gameId);
    assertThat(folds).as("the versions alone fold nothing").hasValue(0);

    versioned.read();
    assertThat(folds).as("the read folds").hasValue(1);
    verify(athletes).findAllVersioned(anyCollection());
    verify(referees).findAllVersioned(anyCollection());
    verify(athletes, never()).findAll(anyCollection());
    verify(referees, never()).findAll(anyCollection());
  }

  /** One failing result rule must not take every game down with it. */
  @Test
  void resultRulesThatThrow_leaveTheGameReadAndWritten_withNoSuggestion_andTheWarning() {
    ResultRules failing =
        standing -> {
          throw new IllegalStateException("a rule that fails");
        };
    KumiteGameService degraded =
        new KumiteGameService(
            repository,
            new KumiteGameWriter(repository, failing),
            athletes,
            referees,
            properties(Duration.ofMinutes(2), 3),
            failing,
            TestClock.FIXED);
    Recording created =
        degraded.create(
            TestGames.newGame(GameFormat.ELIMINATION, false, false), TestGames.SCOREKEEPER);
    String gameId = created.game().game().id();

    degraded.recordScore(
        gameId,
        new NewScore("s1", Side.AKA, ScoreLevel.IPPON, ScoreSource.JUDGES, null, null),
        TestGames.SCOREKEEPER);

    GameRead read = degraded.get(gameId);
    assertThat(created.raisedWarnings()).containsExactly(Warning.SUGGESTION_UNAVAILABLE);
    assertThat(read.game().scores(Side.AKA)).extracting(Score::id).containsExactly("s1");
    assertThat(read.state().side(Side.AKA).total()).isEqualTo(3);
    assertThat(read.state().suggested()).isNull();
    assertThat(read.state().warnings())
        .extracting(GameWarning::code)
        .containsExactly(Warning.SUGGESTION_UNAVAILABLE);
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
        null,
        "scorekeeper");
    long read = service.get(gameId).version();
    willThrow(new OptimisticLockingFailureException("stale version"))
        .given(repository)
        .save(any(KumiteGameDocument.class));

    assertThatThrownBy(() ->
            service.recordClockAction(
                gameId,
                new NewClockAction(
                    "c2", Timer.MAIN, null, ClockActionKind.SET_TIME, 119_000, 60_000L, null),
                read,
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
    long read = service.get(gameId).version();
    willAnswer(
            call -> {
              KumiteGameDocument stored = storage.findById(KumiteGameDocument.class, gameId).get();
              KumiteGame other = KumiteGameDocumentMapper.toDomain(stored, TestGames.rules());
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
                read,
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
    long read = service.get(gameId).version();
    willAnswer(
            call -> {
              KumiteGameDocument stored = storage.findById(KumiteGameDocument.class, gameId).get();
              KumiteGame other = KumiteGameDocumentMapper.toDomain(stored, TestGames.rules());
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
                read,
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

  /** The version a screen read before another scorekeeper's write moved the game on. */
  private long staleVersion(String gameId) {
    return service.get(gameId).version() - 1;
  }

  private static NewSettings ninetySeconds() {
    return new NewSettings(
        90_000,
        List.of(90_000L),
        Map.of(ScoreLevel.IPPON, 3, ScoreLevel.WAZA_ARI, 2, ScoreLevel.YUKO, 1),
        8,
        15_000,
        1_500,
        3,
        4,
        List.of(10_000L),
        180_000,
        1_000);
  }

  @Test
  void settingsChange_withStaleVersion_answersTheCurrentGame_andWritesNothing() {
    String gameId = created();
    long stale = staleVersion(gameId);
    clearInvocations(repository);

    assertThatThrownBy(() -> service.changeSettings(gameId, ninetySeconds(), stale))
        .isInstanceOfSatisfying(
            GameChangedException.class,
            changed ->
                assertThat(changed.current().game().settings().durationMs()).isEqualTo(120_000));
    verify(repository, never()).save(any(KumiteGameDocument.class));
  }

  @Test
  void setTime_withStaleVersion_answersTheCurrentGame_andWritesNothing() {
    String gameId = created();
    long stale = staleVersion(gameId);
    clearInvocations(repository);

    assertThatThrownBy(() ->
            service.recordClockAction(
                gameId,
                new NewClockAction(
                    "c1", Timer.MAIN, null, ClockActionKind.SET_TIME, 120_000, 60_000L, null),
                stale,
                "scorekeeper"))
        .isInstanceOfSatisfying(
            GameChangedException.class,
            changed -> assertThat(changed.current().game().clockActions()).isEmpty());
    verify(repository, never()).save(any(KumiteGameDocument.class));
  }

  @Test
  void result_withStaleVersion_answersTheCurrentGame_andWritesNothing() {
    String gameId = created();
    long stale = staleVersion(gameId);
    clearInvocations(repository);

    assertThatThrownBy(() ->
            service.recordResult(
                gameId,
                new NewResult("r1", Outcome.WIN, Side.AKA, ResultReason.KIKEN, null, false),
                stale,
                "scorekeeper"))
        .isInstanceOfSatisfying(
            GameChangedException.class,
            changed -> assertThat(changed.current().game().results()).isEmpty());
    verify(repository, never()).save(any(KumiteGameDocument.class));
  }

  @Test
  void statusChange_withStaleVersion_answersTheCurrentGame_andWritesNothing() {
    String gameId = created();
    long stale = staleVersion(gameId);
    clearInvocations(repository);

    assertThatThrownBy(() ->
            service.recordStatusChange(
                gameId,
                new NewStatusChange("v1", StatusChangeKind.VOID, "never fought"),
                stale,
                ActingRole.ADMIN,
                "admin"))
        .isInstanceOfSatisfying(
            GameChangedException.class,
            changed ->
                assertThat(changed.current().state().status()).isNotEqualTo(GameStatus.VOIDED));
    verify(repository, never()).save(any(KumiteGameDocument.class));
  }

  @Test
  void result_withCurrentVersion_isRecorded() {
    String gameId = created();
    long current = service.get(gameId).version();

    Recording recorded =
        service.recordResult(
            gameId,
            new NewResult("r1", Outcome.WIN, Side.AKA, ResultReason.KIKEN, null, false),
            current,
            "scorekeeper");

    assertThat(recorded.game().state().status()).isEqualTo(GameStatus.DECIDED);
    assertThat(service.get(gameId).game().results()).hasSize(1);
  }

  /**
   * The first attempt landed and moved the game on, so the retry's version is stale: it still
   * answers as a retry does, with the stored warnings and nothing written.
   */
  @Test
  void retriedResult_whoseFirstAttemptLanded_answersAsRetry_withStaleVersion() {
    String gameId = created();
    long read = service.get(gameId).version();
    NewResult kiken = new NewResult("r1", Outcome.WIN, Side.AKA, ResultReason.KIKEN, null, false);
    service.recordResult(gameId, kiken, read, "scorekeeper");
    clearInvocations(repository);

    Recording retry = service.recordResult(gameId, kiken, read - 1, "scorekeeper");

    verify(repository, never()).save(any(KumiteGameDocument.class));
    assertThat(retry.game().game().results()).hasSize(1);
  }

  @Test
  void setTime_withoutVersion_isRefused_andWritesNothing() {
    String gameId = created();
    clearInvocations(repository);

    assertThatThrownBy(() ->
            service.recordClockAction(
                gameId,
                new NewClockAction(
                    "c1", Timer.MAIN, null, ClockActionKind.SET_TIME, 120_000, 60_000L, null),
                null,
                "scorekeeper"))
        .isInstanceOf(InvalidGameRequestException.class)
        .hasMessageContaining("expectedVersion");
    verify(repository, never()).save(any(KumiteGameDocument.class));
  }

  /**
   * An appended clock action carries no version: one sent would read as a check that is not made.
   */
  @Test
  void appendedClockAction_withVersion_isRefused_andWritesNothing() {
    String gameId = created();
    long current = service.get(gameId).version();
    clearInvocations(repository);

    assertThatThrownBy(() ->
            service.recordClockAction(
                gameId,
                new NewClockAction(
                    "c1", Timer.MAIN, null, ClockActionKind.START, 120_000, null, null),
                current,
                "scorekeeper"))
        .isInstanceOf(InvalidGameRequestException.class)
        .hasMessageContaining("expectedVersion");
    verify(repository, never()).save(any(KumiteGameDocument.class));
  }
}
