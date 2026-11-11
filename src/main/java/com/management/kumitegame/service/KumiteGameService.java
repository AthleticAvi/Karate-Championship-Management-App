package com.management.kumitegame.service;

import com.management.athlete.service.AthleteService;
import com.management.config.KumiteGameProperties;
import com.management.kumitegame.data.KumiteGameDocumentMapper;
import com.management.kumitegame.data.KumiteGameRepository;
import com.management.kumitegame.domain.ActingRole;
import com.management.kumitegame.domain.Assignment;
import com.management.kumitegame.domain.AssignmentKind;
import com.management.kumitegame.domain.ClockActionKind;
import com.management.kumitegame.domain.GameChangedException;
import com.management.kumitegame.domain.GameNotFoundException;
import com.management.kumitegame.domain.GameRead;
import com.management.kumitegame.domain.GameState;
import com.management.kumitegame.domain.GameWarning;
import com.management.kumitegame.domain.InvalidGameRequestException;
import com.management.kumitegame.domain.KumiteGame;
import com.management.kumitegame.domain.NewAmendment;
import com.management.kumitegame.domain.NewAssignment;
import com.management.kumitegame.domain.NewClockAction;
import com.management.kumitegame.domain.NewGame;
import com.management.kumitegame.domain.NewPenalty;
import com.management.kumitegame.domain.NewResult;
import com.management.kumitegame.domain.NewScore;
import com.management.kumitegame.domain.NewSenshuRecord;
import com.management.kumitegame.domain.NewSettings;
import com.management.kumitegame.domain.NewStatusChange;
import com.management.kumitegame.domain.PanelMember;
import com.management.kumitegame.domain.Recorded;
import com.management.kumitegame.domain.Recording;
import com.management.referee.service.RefereeService;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * The game aggregate: create, read, and every action recorded on a game, each one write to one
 * document.
 *
 * <p>Athletes and referees are read, never written: a game holds their ids, and their names are
 * looked up when the game is read. An athlete or referee a game is given must exist, and is read
 * before anything is written.
 *
 * <p><strong>Appends retry, absolute actions do not.</strong> A score, penalty, clock action,
 * SENSHU record, assignment or amendment goes through {@link KumiteGameWriter#append}. Setting the
 * time, recording a result, changing the settings, and approving, reopening or voiding set a value,
 * so a lost race answers 409 with the game as it now stands: see {@code [version-writes]} in {@code
 * context/workflow/design/concurrency-consistency.md}.
 */
@Service
public class KumiteGameService {

  private final KumiteGameRepository kumiteGameRepository;
  private final KumiteGameWriter writer;
  private final AthleteService athleteService;
  private final RefereeService refereeService;
  private final KumiteGameProperties properties;
  private final Clock clock;

  public KumiteGameService(
      KumiteGameRepository kumiteGameRepository,
      KumiteGameWriter writer,
      AthleteService athleteService,
      RefereeService refereeService,
      KumiteGameProperties properties,
      Clock clock) {
    this.kumiteGameRepository = kumiteGameRepository;
    this.writer = writer;
    this.athleteService = athleteService;
    this.refereeService = refereeService;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Creates a game with a copy of the configured settings. Everything that can fail does so before
   * the one insert: the athletes and referees are read first.
   *
   * <p>Not retried by id: a retried create makes a second game. The id and the ids of the first
   * entries are given here.
   */
  public Recording create(NewGame newGame, String recordedBy) {
    requireAthletes(List.of(newGame.akaAthleteId(), newGame.aoAthleteId()));
    requireReferees(newGame.panel().stream().map(PanelMember::refereeId).toList());
    KumiteGame game =
        KumiteGame.create(
            UUID.randomUUID().toString(),
            newGame,
            properties.rules().snapshot(),
            () -> UUID.randomUUID().toString(),
            recordedBy,
            clock.instant());
    KumiteGame created =
        KumiteGameDocumentMapper.toDomain(
            kumiteGameRepository.insert(KumiteGameDocumentMapper.toNewDocument(game)));
    GameRead read = read(created);
    return new Recording(read, read.state().warnings().stream().map(GameWarning::code).toList());
  }

  public GameRead get(String gameId) {
    return read(
        KumiteGameDocumentMapper.toDomain(
            kumiteGameRepository
                .findById(gameId)
                .orElseThrow(() -> new GameNotFoundException("No game has the id " + gameId))));
  }

  public Recording changeSettings(String gameId, NewSettings settings) {
    return absolute(gameId, game -> game.changeSettings(settings));
  }

  public Recording recordScore(String gameId, NewScore score, String recordedBy) {
    return append(gameId, game -> game.recordScore(score, recordedBy, clock.instant()));
  }

  public Recording recordPenalty(String gameId, NewPenalty penalty, String recordedBy) {
    return append(gameId, game -> game.recordPenalty(penalty, recordedBy, clock.instant()));
  }

  /** SET_TIME sets a value, so it is absolute; every other clock action is appended. */
  public Recording recordClockAction(String gameId, NewClockAction action, String recordedBy) {
    Function<KumiteGame, Recorded> record =
        game -> game.recordClockAction(action, recordedBy, clock.instant());
    return action.action() == ClockActionKind.SET_TIME
        ? absolute(gameId, record)
        : append(gameId, record);
  }

  public Recording recordSenshu(String gameId, NewSenshuRecord record, String recordedBy) {
    return append(gameId, game -> game.recordSenshu(record, recordedBy, clock.instant()));
  }

  /** The athlete or referee it names must exist, and is read before anything is written. */
  public Recording recordAssignment(String gameId, NewAssignment assignment, String recordedBy) {
    String athleteId = assignment.athleteId();
    String refereeId = assignment.refereeId();
    if (assignment.kind() == AssignmentKind.ATHLETE && athleteId != null) {
      requireAthletes(List.of(athleteId));
    }
    if (assignment.kind() == AssignmentKind.PANEL_ADD && refereeId != null) {
      requireReferees(List.of(refereeId));
    }
    return append(gameId, game -> game.recordAssignment(assignment, recordedBy, clock.instant()));
  }

  public Recording recordAmendment(String gameId, NewAmendment amendment, String recordedBy) {
    return append(gameId, game -> game.recordAmendment(amendment, recordedBy, clock.instant()));
  }

  public Recording recordResult(String gameId, NewResult result, String recordedBy) {
    return absolute(gameId, game -> game.recordResult(result, recordedBy, clock.instant()));
  }

  /**
   * Approves, reopens or voids. Absolute: a move chosen against a status that has since changed is
   * not what the caller meant.
   *
   * @param role the role the signed-in account acts under, which some moves need
   */
  public Recording recordStatusChange(
      String gameId, NewStatusChange change, ActingRole role, String recordedBy) {
    return absolute(
        gameId, game -> game.recordStatusChange(change, role, recordedBy, clock.instant()));
  }

  private Recording append(String gameId, Function<KumiteGame, Recorded> action) {
    KumiteGameWriter.Applied applied = writer.append(gameId, action);
    return new Recording(read(applied.game()), applied.recorded().raisedWarnings());
  }

  private Recording absolute(String gameId, Function<KumiteGame, Recorded> action) {
    try {
      KumiteGameWriter.Applied applied = writer.applyOnce(gameId, action);
      return new Recording(read(applied.game()), applied.recorded().raisedWarnings());
    } catch (OptimisticLockingFailureException lost) {
      throw new GameChangedException(get(gameId), lost);
    }
  }

  /** The game with the athletes and referees it names, as they now stand. */
  private GameRead read(KumiteGame game) {
    GameState state = game.state();
    List<Assignment> assignments = game.assignments();
    Set<String> athleteIds =
        Set.copyOf(
            assignments.stream().map(Assignment::athleteId).filter(Objects::nonNull).toList());
    Set<String> refereeIds =
        Set.copyOf(
            assignments.stream().map(Assignment::refereeId).filter(Objects::nonNull).toList());
    return new GameRead(
        game,
        state,
        athleteService.findAll(athleteIds),
        refereeService.findAll(refereeIds),
        clock.instant());
  }

  private void requireAthletes(Collection<String> athleteIds) {
    requireAll(athleteIds, athleteService.findAll(athleteIds).keySet(), "athlete");
  }

  private void requireReferees(Collection<String> refereeIds) {
    requireAll(refereeIds, refereeService.findAll(refereeIds).keySet(), "referee");
  }

  /** Refuses the first id that was not found, before anything is written. */
  private static void requireAll(Collection<String> ids, Set<String> found, String noun) {
    ids.stream()
        .filter(id -> !found.contains(id))
        .findFirst()
        .ifPresent(missing -> {
          throw new InvalidGameRequestException("No " + noun + " has the id " + missing + ".");
        });
  }
}
