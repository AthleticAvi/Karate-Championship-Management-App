package com.management.kumitegame.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * A Kumite game: what happened, as ordered entries, and the rules it ran under. Everything else is
 * derived from these by {@link #state()}.
 *
 * <p><strong>Officials decide, the system records.</strong> An action is refused only when the data
 * would contradict itself. A rule breach the system can see is applied, and the warning is stored
 * on the entry and returned. The warnings are separate rule classes reading the settings snapshot;
 * which status accepts which action is {@link EntryKind}.
 *
 * <p><strong>A retried action applies once.</strong> Every entry carries the id the client gave it.
 * An action whose id is already on record is the same request when what the client sent, and who
 * sent it, match the stored entry: nothing is applied, and the stored warnings answer. Any other
 * reuse of the id is refused. The check is per game, inside the versioned save.
 *
 * <p>Knows nothing of storage: the stored form is {@code
 * com.management.kumitegame.data.KumiteGameDocument}.
 */
public final class KumiteGame {

  private final String id;
  private final GameFormat format;
  private final boolean medalGame;
  private final boolean extraGame;
  private Settings settings;
  private final Map<Side, List<Score>> scores = new EnumMap<>(Side.class);
  private final Map<Side, List<Penalty>> penalties = new EnumMap<>(Side.class);
  private final List<Assignment> assignments;
  private final List<ClockAction> clockActions;
  private final List<SenshuRecord> senshuRecords;
  private final List<Amendment> amendments;
  private final List<Result> results;
  private final List<StatusChange> statusChanges;
  private final ResultRules rules;

  /**
   * A game as stored. Any history loads: see {@link GameFold}.
   *
   * @param rules the result rules the game is folded with, so every read sees the suggestion
   */
  public KumiteGame(
      String id,
      GameFormat format,
      boolean medalGame,
      boolean extraGame,
      Settings settings,
      Map<Side, List<Score>> scores,
      Map<Side, List<Penalty>> penalties,
      List<Assignment> assignments,
      List<ClockAction> clockActions,
      List<SenshuRecord> senshuRecords,
      List<Amendment> amendments,
      List<Result> results,
      List<StatusChange> statusChanges,
      ResultRules rules) {
    this.id = id;
    this.format = format;
    this.medalGame = medalGame;
    this.extraGame = extraGame;
    this.settings = settings;
    for (Side side : Side.values()) {
      this.scores.put(side, new ArrayList<>(scores.getOrDefault(side, List.of())));
      this.penalties.put(side, new ArrayList<>(penalties.getOrDefault(side, List.of())));
    }
    this.assignments = new ArrayList<>(assignments);
    this.clockActions = new ArrayList<>(clockActions);
    this.senshuRecords = new ArrayList<>(senshuRecords);
    this.amendments = new ArrayList<>(amendments);
    this.results = new ArrayList<>(results);
    this.statusChanges = new ArrayList<>(statusChanges);
    this.rules = Objects.requireNonNull(rules);
  }

  /**
   * A new game: the athlete on each side and the panel become its first entries.
   *
   * @param entryIds gives each of those entries its id: a creation is not retried by id, so the
   *     server names them
   */
  public static KumiteGame create(
      String id,
      NewGame newGame,
      Settings settings,
      Supplier<String> entryIds,
      String recordedBy,
      Instant now,
      ResultRules rules) {
    if (newGame.akaAthleteId().equals(newGame.aoAthleteId())) {
      throw new InvalidGameRequestException(
          "An athlete cannot fight themselves: both sides were given athlete "
              + newGame.akaAthleteId()
              + ".");
    }
    Set<String> referees = new HashSet<>();
    newGame
        .panel()
        .forEach(member -> {
          if (!referees.add(member.refereeId())) {
            throw new InvalidGameRequestException(
                "Referee " + member.refereeId() + " is on the panel more than once.");
          }
        });

    List<Assignment> assignments = new ArrayList<>();
    Map<Side, String> athletes =
        Map.of(Side.AKA, newGame.akaAthleteId(), Side.AO, newGame.aoAthleteId());
    for (Side side : Side.values()) {
      assignments.add(
          new Assignment(
              AssignmentKind.ATHLETE,
              side,
              athletes.get(side),
              null,
              null,
              meta(entryIds.get(), assignments.size() + 1, recordedBy, now, List.of())));
    }
    for (PanelMember member : newGame.panel()) {
      assignments.add(
          new Assignment(
              AssignmentKind.PANEL_ADD,
              null,
              null,
              member.refereeId(),
              member.role(),
              meta(entryIds.get(), assignments.size() + 1, recordedBy, now, List.of())));
    }
    return new KumiteGame(
        id,
        newGame.format(),
        newGame.medalGame(),
        newGame.extraGame(),
        settings,
        Map.of(),
        Map.of(),
        assignments,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        rules);
  }

  /** Everything derived from the entries. */
  public GameState state() {
    return GameFold.fold(this);
  }

  /**
   * Replaces the settings, keeping the rulebook version. Only before the start: a game never
   * changes its rules mid-way. No history is kept, since nothing has happened yet.
   */
  public Recorded changeSettings(NewSettings newSettings) {
    GameState state = state();
    refuseIfFinal(EntryKind.SETTINGS_CHANGE, state);
    Settings changed = newSettings.under(settings.rulebookVersion());
    admit(EntryKind.SETTINGS_CHANGE, state);
    this.settings = changed;
    return Recorded.applied(List.of());
  }

  public Recorded recordScore(NewScore score, String recordedBy, Instant now) {
    Optional<Recorded> replay =
        replayOf(
            score.id(),
            found ->
                found.entry() instanceof Score stored
                    && score.matches(stored, found.side(), recordedBy));
    if (replay.isPresent()) {
      return replay.get();
    }
    GameState state = state();
    List<Warning> raised = admit(EntryKind.SCORE, state);
    Integer exchange = exchangeOf(score.exchange(), state);
    LateScore.check(state, now, settings, raised);
    SenshuWarnings.checkScore(score.side(), exchange, standingAward(state).orElse(null), raised);
    if (isCurrentExchange(score.exchange(), state)) {
      ClockTolerance.checkClockRemaining(
          state.mainClock(), score.clockRemainingMs(), now, settings, raised);
    }
    return append(
        scoresOf(score.side()),
        meta -> new Score(score.level(), score.source(), exchange, score.clockRemainingMs(), meta),
        score.id(),
        state,
        recordedBy,
        now,
        raised);
  }

  public Recorded recordPenalty(NewPenalty penalty, String recordedBy, Instant now) {
    Optional<Recorded> replay =
        replayOf(
            penalty.id(),
            found ->
                found.entry() instanceof Penalty stored
                    && penalty.matches(stored, found.side(), recordedBy));
    if (replay.isPresent()) {
      return replay.get();
    }
    GameState state = state();
    List<Warning> raised = admit(EntryKind.PENALTY, state);
    Integer exchange = exchangeOf(penalty.exchange(), state);
    PenaltyLadder.check(state.side(penalty.side()), penalty.level(), settings, raised);
    SenshuWarnings.checkPenalty(
        state,
        penalty.side(),
        penalty.infraction(),
        SenshuFold.inFinalWindow(
            penalty.exchange(),
            penalty.clockRemainingMs(),
            state.currentExchange(),
            state.mainClock(),
            now,
            settings),
        raised);
    if (isCurrentExchange(penalty.exchange(), state)) {
      ClockTolerance.checkClockRemaining(
          state.mainClock(), penalty.clockRemainingMs(), now, settings, raised);
    }
    return append(
        penaltiesOf(penalty.side()),
        meta ->
            new Penalty(
                penalty.level(), penalty.infraction(), exchange, penalty.clockRemainingMs(), meta),
        penalty.id(),
        state,
        recordedBy,
        now,
        raised);
  }

  public Recorded recordClockAction(NewClockAction action, String recordedBy, Instant now) {
    Optional<Recorded> replay =
        replayOf(
            action.id(),
            found ->
                found.entry() instanceof ClockAction stored && action.matches(stored, recordedBy));
    if (replay.isPresent()) {
      return replay.get();
    }
    GameState state = state();
    refuseIfFinal(EntryKind.CLOCK_ACTION, state);
    requireWellFormed(action);
    List<Warning> raised = admit(EntryKind.CLOCK_ACTION, state);
    ClockTolerance.checkTimeShown(
        state.clock(action.timer(), action.side()), action.timeShownMs(), now, settings, raised);
    return append(
        clockActions,
        meta ->
            new ClockAction(
                action.timer(),
                action.side(),
                action.action(),
                action.timeShownMs(),
                action.amountMs(),
                action.pauseReason(),
                meta),
        action.id(),
        state,
        recordedBy,
        now,
        raised);
  }

  /**
   * Records SENSHU as the officials awarded or withdrew it. Refused only when it would contradict
   * itself: an AWARDED while a side holds SENSHU, since both cannot hold it. A WITHDRAWN from a
   * side that does not hold it is recorded with a warning, and changes nothing.
   */
  public Recorded recordSenshu(NewSenshuRecord record, String recordedBy, Instant now) {
    Optional<Recorded> replay =
        replayOf(
            record.id(),
            found ->
                found.entry() instanceof SenshuRecord stored && record.matches(stored, recordedBy));
    if (replay.isPresent()) {
      return replay.get();
    }
    GameState state = state();
    refuseIfFinal(EntryKind.SENSHU_RECORD, state);
    Integer exchange = exchangeOf(record.exchange(), state);
    Side holder = state.senshu().holder();
    if (record.kind() == SenshuRecordKind.AWARDED && holder != null) {
      throw new ActionRefusedException(
          "SENSHU is held by " + holder + ": withdraw it before awarding it again.");
    }
    List<Warning> raised = admit(EntryKind.SENSHU_RECORD, state);
    if (isCurrentExchange(record.exchange(), state)) {
      ClockTolerance.checkClockRemaining(
          state.mainClock(), record.clockRemainingMs(), now, settings, raised);
    }
    if (record.kind() == SenshuRecordKind.AWARDED) {
      SenshuWarnings.checkAward(state, exchange, scoresOf(record.side().opponent()), raised);
    } else {
      SenshuWarnings.checkWithdrawal(state, record.side(), raised);
    }
    return append(
        senshuRecords,
        meta ->
            new SenshuRecord(
                record.side(),
                record.kind(),
                record.reason(),
                exchange,
                record.clockRemainingMs(),
                meta),
        record.id(),
        state,
        recordedBy,
        now,
        raised);
  }

  /**
   * Puts an athlete on a side, or changes the panel. Changing the panel applies as given: adding a
   * referee already on it changes their role, and removing one not on it changes nothing.
   */
  public Recorded recordAssignment(NewAssignment assignment, String recordedBy, Instant now) {
    Optional<Recorded> replay =
        replayOf(
            assignment.id(),
            found ->
                found.entry() instanceof Assignment stored
                    && assignment.matches(stored, recordedBy));
    if (replay.isPresent()) {
      return replay.get();
    }
    GameState state = state();
    refuseIfFinal(EntryKind.ASSIGNMENT, state);
    requireWellFormed(assignment);
    Side side = assignment.side();
    String athleteId = assignment.athleteId();
    if (assignment.kind() == AssignmentKind.ATHLETE
        && side != null
        && athleteId != null
        && athleteId.equals(state.side(side.opponent()).athleteId())) {
      throw new ActionRefusedException(
          "Athlete "
              + athleteId
              + " is on "
              + side.opponent()
              + ", so cannot also be on "
              + side
              + ".");
    }
    List<Warning> raised = admit(EntryKind.ASSIGNMENT, state);
    return append(
        assignments,
        meta ->
            new Assignment(
                assignment.kind(),
                side,
                assignment.athleteId(),
                assignment.refereeId(),
                assignment.role(),
                meta),
        assignment.id(),
        state,
        recordedBy,
        now,
        raised);
  }

  /**
   * Cancels a score, penalty or SENSHU record, or acknowledges the warnings on any entry.
   *
   * <p>Refused when it would contradict itself: a target that does not exist, a CANCEL of anything
   * but a score, penalty or SENSHU record (a CANCEL included), a second CANCEL of the same target,
   * or a CANCEL of a withdrawal that would give SENSHU back while a later award stands. To undo a
   * mistaken cancel, record the entry again.
   */
  public Recorded recordAmendment(NewAmendment amendment, String recordedBy, Instant now) {
    Optional<Recorded> replay =
        replayOf(
            amendment.id(),
            found ->
                found.entry() instanceof Amendment stored && amendment.matches(stored, recordedBy));
    if (replay.isPresent()) {
      return replay.get();
    }
    GameState state = state();
    refuseIfFinal(EntryKind.AMENDMENT, state);
    Entry target =
        entries()
            .map(SidedEntry::entry)
            .filter(entry -> entry.id().equals(amendment.targetId()))
            .findFirst()
            .orElseThrow(() ->
                new ActionRefusedException(
                    "No entry has the id " + amendment.targetId() + " in this game."));
    if (amendment.kind() == AmendmentKind.CANCEL) {
      if (!(target instanceof Score
          || target instanceof Penalty
          || target instanceof SenshuRecord)) {
        throw new ActionRefusedException(
            "Only a score, a penalty or a SENSHU record can be cancelled, and "
                + amendment.targetId()
                + " is none of these.");
      }
      if (state.isCancelled(amendment.targetId())) {
        throw new ActionRefusedException(
            "Entry " + amendment.targetId() + " is already cancelled.");
      }
      if (target instanceof SenshuRecord && wouldGiveSenshuToTwo(state, amendment.targetId())) {
        throw new ActionRefusedException(
            "Cancelling "
                + amendment.targetId()
                + " would give SENSHU back while a later award stands: cancel that award first.");
      }
    }
    List<Warning> raised = admit(EntryKind.AMENDMENT, state);
    if (amendment.kind() == AmendmentKind.CANCEL) {
      cancelledScoreSide(amendment.targetId())
          .filter(side -> state.senshu().isHeldBy(side))
          .ifPresent(holder ->
              SenshuWarnings.checkCancelledScore(
                  scoresOf(holder),
                  scoresOf(holder.opponent()),
                  id -> !state.isCancelled(id) && !id.equals(amendment.targetId()),
                  raised));
    }
    return append(
        amendments,
        meta -> new Amendment(amendment.kind(), amendment.targetId(), amendment.reason(), meta),
        amendment.id(),
        state,
        recordedBy,
        now,
        raised);
  }

  /**
   * Records a result as given. A later one supersedes it, and it stays on record. The suggestion it
   * was recorded against is kept on it, and a result that does not agree with it is recorded with a
   * warning: see {@link SuggestedResult}.
   */
  public Recorded recordResult(NewResult result, String recordedBy, Instant now) {
    Optional<Recorded> replay =
        replayOf(
            result.id(),
            found -> found.entry() instanceof Result stored && result.matches(stored, recordedBy));
    if (replay.isPresent()) {
      return replay.get();
    }
    GameState state = state();
    refuseIfFinal(EntryKind.RESULT, state);
    if ((result.outcome() == Outcome.WIN) != (result.winner() != null)) {
      throw new InvalidGameRequestException(
          "A WIN names a winner, and a "
              + result.outcome()
              + (result.outcome() == Outcome.WIN ? " needs one." : " has none."));
    }
    List<Warning> raised = new ArrayList<>();
    if (state.status() != GameStatus.NOT_STARTED || !result.reason().isBeforeTheStart()) {
      raised = admit(EntryKind.RESULT, state);
    }
    SuggestedResult suggested = state.suggested();
    if (!SuggestedResult.agrees(suggested, result)) {
      raised.add(Warning.RESULT_DIFFERS_FROM_SUGGESTION);
    }
    results.add(
        new Result(
            result.outcome(),
            result.winner(),
            result.reason(),
            result.reasonDetail(),
            result.bothDisqualified(),
            suggested,
            next(result.id(), state, recordedBy, now, raised)));
    return Recorded.applied(raised);
  }

  /**
   * Approves, reopens or voids the game: the moves no other entry causes.
   *
   * <p>Refused when the status does not allow the move. Reopening an APPROVED game and voiding are
   * an Admin's: refused to a Scorekeeper, and refused without a reason. Reopening a DECIDED game
   * returns it to the status it had before the result, which the reopen supersedes; reopening an
   * APPROVED one returns it to DECIDED, with its result standing.
   *
   * @param role the role the signed-in account acts under
   */
  public Recorded recordStatusChange(
      NewStatusChange change, ActingRole role, String recordedBy, Instant now) {
    Optional<Recorded> replay =
        replayOf(
            change.id(),
            found ->
                found.entry() instanceof StatusChange stored && change.matches(stored, recordedBy));
    if (replay.isPresent()) {
      return replay.get();
    }
    GameState state = state();
    List<Warning> raised = admit(EntryKind.of(change.kind()), state);
    if (change.kind().isAdminOnly(state.status())) {
      if (role != ActingRole.ADMIN) {
        throw new ActionForbiddenException(
            "Only an Admin may " + verb(change.kind()) + " a game that is " + state.status() + ".");
      }
      String reason = change.reason();
      if (reason == null || reason.isBlank()) {
        throw new InvalidGameRequestException(
            "A reason is needed to "
                + verb(change.kind())
                + " a game that is "
                + state.status()
                + ".");
      }
    }
    statusChanges.add(
        new StatusChange(
            change.kind(), change.reason(), next(change.id(), state, recordedBy, now, raised)));
    return Recorded.applied(raised);
  }

  /**
   * Appends an entry. While DECIDED, an entry is a correction: when the result that stands agreed
   * with the suggestion before it and does not after, it carries a warning, and nothing reopens.
   */
  private <E extends Entry> Recorded append(
      List<E> list,
      Function<EntryMeta, E> entry,
      String entryId,
      GameState before,
      String recordedBy,
      Instant now,
      List<Warning> raised) {
    list.add(entry.apply(next(entryId, before, recordedBy, now, raised)));
    if (before.status() == GameStatus.DECIDED && suggestionNowDiffers(before, state())) {
      raised.add(Warning.SUGGESTION_DIFFERS_FROM_RESULT);
      list.set(list.size() - 1, entry.apply(next(entryId, before, recordedBy, now, raised)));
    }
    return Recorded.applied(raised);
  }

  /**
   * The result that stands agreed with the suggestion before the correction, and does not now. A
   * result that already differed was warned about when it began to differ.
   */
  private static boolean suggestionNowDiffers(GameState before, GameState after) {
    Result was = before.currentResult();
    Result now = after.currentResult();
    return was != null
        && now != null
        && SuggestedResult.agrees(before.suggested(), was)
        && !SuggestedResult.agrees(after.suggested(), now);
  }

  private static String verb(StatusChangeKind kind) {
    return kind.name().toLowerCase(Locale.ROOT);
  }

  /**
   * Whether SENSHU, replayed as this CANCEL would leave it, meets an award while a side holds it.
   * Only a cancelled withdrawal can do that.
   */
  private boolean wouldGiveSenshuToTwo(GameState state, String targetId) {
    List<SenshuRecord> inOrder =
        senshuRecords.stream()
            .sorted(Comparator.comparingInt(record -> record.meta().order()))
            .toList();
    Set<String> before = skippedIds(state);
    return SenshuFold.replay(
            inOrder, id -> state.isCancelled(id) || id.equals(targetId), record -> false)
        .conflicting()
        .stream()
        .anyMatch(id -> !before.contains(id));
  }

  /**
   * The award the holder holds SENSHU by: the latest AWARDED of the holder's side that is neither
   * cancelled nor skipped by the fold.
   */
  private Optional<SenshuRecord> standingAward(GameState state) {
    Set<String> skipped = skippedIds(state);
    return senshuRecords.stream()
        .filter(record -> record.kind() == SenshuRecordKind.AWARDED)
        .filter(record -> state.senshu().isHeldBy(record.side()))
        .filter(record -> !state.isCancelled(record.id()) && !skipped.contains(record.id()))
        .max(Comparator.comparingInt(record -> record.meta().order()));
  }

  /** The entries the fold skipped as history it cannot fold as written. */
  private static Set<String> skippedIds(GameState state) {
    return state.warnings().stream()
        .filter(warning -> warning.code() == Warning.HISTORY_INCONSISTENT)
        .map(GameWarning::entryId)
        .filter(Objects::nonNull)
        .collect(Collectors.toUnmodifiableSet());
  }

  /** The side whose list holds the score this id names, if it names a score. */
  private Optional<Side> cancelledScoreSide(String targetId) {
    return Stream.of(Side.values())
        .filter(side -> scoresOf(side).stream().anyMatch(score -> score.id().equals(targetId)))
        .findFirst();
  }

  /** Every entry, in the order it was recorded. */
  List<SidedEntry> entriesInOrder() {
    return entries()
        .sorted(Comparator.comparingInt(placed -> placed.entry().meta().order()))
        .toList();
  }

  private Stream<SidedEntry> entries() {
    Stream<SidedEntry> sided =
        Stream.of(Side.values())
            .flatMap(side ->
                Stream.concat(scoresOf(side).stream(), penaltiesOf(side).stream())
                    .map(entry -> new SidedEntry(entry, side)));
    Stream<SidedEntry> unsided =
        Stream.of(assignments, clockActions, senshuRecords, amendments, results, statusChanges)
            .flatMap(List::stream)
            .map(entry -> new SidedEntry(entry, null));
    return Stream.concat(sided, unsided);
  }

  /** The stored answer when this id is already on record, or a refusal if it was another action. */
  private Optional<Recorded> replayOf(String entryId, Predicate<SidedEntry> sameRequest) {
    Optional<SidedEntry> found = entries().filter(e -> e.entry().id().equals(entryId)).findFirst();
    if (found.isEmpty()) {
      return Optional.empty();
    }
    if (!sameRequest.test(found.get())) {
      throw new ActionRefusedException(
          "Entry id " + entryId + " was already used for a different action in this game.");
    }
    return Optional.of(Recorded.replayOf(found.get().entry().meta()));
  }

  /**
   * An entry for an earlier exchange, such as a video review, was sent with the time of that
   * exchange, so the clock as it is now says nothing about it.
   */
  private static boolean isCurrentExchange(@Nullable Integer named, GameState state) {
    return named == null || named.equals(state.currentExchange());
  }

  /**
   * The exchange an entry belongs to: the one it names, or the current one, empty before the start.
   * It may name the current exchange or an earlier one, as a video review does. One later than the
   * current exchange, any before the first start, or one below 1, has not happened, so the record
   * would contradict itself.
   */
  private static @Nullable Integer exchangeOf(@Nullable Integer named, GameState state) {
    if (named == null) {
      return state.currentExchange();
    }
    if (named < 1 || named > state.exchange()) {
      throw new ActionRefusedException(
          "Exchange "
              + named
              + " has not happened yet: "
              + (state.exchange() == 0
                  ? "the main clock has not started."
                  : "the current one is " + state.exchange() + "."));
    }
    return named;
  }

  /**
   * A final game refuses every recording before anything else is checked, so the caller is never
   * told to fix what would still be refused.
   */
  private static void refuseIfFinal(EntryKind kind, GameState state) {
    if (state.status().isFinal()) {
      admit(kind, state);
    }
  }

  private static List<Warning> admit(EntryKind kind, GameState state) {
    List<Warning> raised = new ArrayList<>();
    kind.admission(state.status()).apply(kind, state.status(), raised);
    return raised;
  }

  private static EntryMeta next(
      String entryId, GameState state, String recordedBy, Instant now, List<Warning> raised) {
    return meta(entryId, state.lastOrder() + 1, recordedBy, now, raised);
  }

  private static EntryMeta meta(
      String entryId, int order, String recordedBy, Instant now, List<Warning> raised) {
    return new EntryMeta(entryId, order, now, recordedBy, raised);
  }

  private static void requireWellFormed(NewClockAction action) {
    if ((action.timer() == Timer.MEDICAL) != (action.side() != null)) {
      throw new InvalidGameRequestException(
          "A medical timer names its side, and the main clock has none.");
    }
    Long amount = action.amountMs();
    if (action.action().hasAmount() != (amount != null)) {
      throw new InvalidGameRequestException(
          "ADD_TIME and SET_TIME take an amount, and "
              + action.action()
              + " was sent with it "
              + (amount == null ? "missing." : "given."));
    }
    if (amount != null
        && (amount < 0 || (action.action() == ClockActionKind.ADD_TIME && amount == 0))) {
      throw new InvalidGameRequestException(
          "Time added must be more than zero, and time set zero or more, but "
              + amount
              + " ms was given.");
    }
  }

  private static void requireWellFormed(NewAssignment assignment) {
    boolean wellFormed =
        switch (assignment.kind()) {
          case ATHLETE ->
              assignment.side() != null
                  && assignment.athleteId() != null
                  && assignment.refereeId() == null
                  && assignment.role() == null;
          case PANEL_ADD ->
              assignment.refereeId() != null
                  && assignment.role() != null
                  && assignment.side() == null
                  && assignment.athleteId() == null;
          case PANEL_REMOVE ->
              assignment.refereeId() != null
                  && assignment.side() == null
                  && assignment.athleteId() == null;
        };
    if (!wellFormed) {
      throw new InvalidGameRequestException(
          "ATHLETE names a side and an athlete, PANEL_ADD a referee and a role, and PANEL_REMOVE"
              + " a referee, each with nothing else.");
    }
  }

  /** Every side has a list from construction on. */
  private List<Score> scoresOf(Side side) {
    return Objects.requireNonNull(scores.get(side));
  }

  private List<Penalty> penaltiesOf(Side side) {
    return Objects.requireNonNull(penalties.get(side));
  }

  public String id() {
    return id;
  }

  public GameFormat format() {
    return format;
  }

  public boolean isMedalGame() {
    return medalGame;
  }

  public boolean isExtraGame() {
    return extraGame;
  }

  public Settings settings() {
    return settings;
  }

  public List<Score> scores(Side side) {
    return Collections.unmodifiableList(scoresOf(side));
  }

  public List<Penalty> penalties(Side side) {
    return Collections.unmodifiableList(penaltiesOf(side));
  }

  public List<Assignment> assignments() {
    return Collections.unmodifiableList(assignments);
  }

  public List<ClockAction> clockActions() {
    return Collections.unmodifiableList(clockActions);
  }

  public List<SenshuRecord> senshuRecords() {
    return Collections.unmodifiableList(senshuRecords);
  }

  public List<Amendment> amendments() {
    return Collections.unmodifiableList(amendments);
  }

  public List<Result> results() {
    return Collections.unmodifiableList(results);
  }

  public List<StatusChange> statusChanges() {
    return Collections.unmodifiableList(statusChanges);
  }

  ResultRules rules() {
    return rules;
  }

  /** A stored game: equal only to the same game. */
  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof KumiteGame game && id.equals(game.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "KumiteGame[" + id + "]";
  }
}
