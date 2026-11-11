package com.management.kumitegame.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Folds a game's entries, in order, into its {@link GameState}.
 *
 * <p><strong>Never throws.</strong> Validity is checked when an entry is appended, never here, so
 * stored history always loads whatever it holds. An entry this build cannot fold as written, such
 * as a CANCEL of something missing or a second use of an id, is skipped with a warning. A rule
 * tightened later must not make an old game unreadable.
 */
final class GameFold {

  private final Settings settings;
  private GameStatus status = GameStatus.NOT_STARTED;
  private ClockState mainClock;
  private final Map<Side, ClockState> medicalClocks = new EnumMap<>(Side.class);
  private int exchange;
  private final Map<Side, Tally> tallies = new EnumMap<>(Side.class);
  private final Map<String, PanelRole> panel = new LinkedHashMap<>();
  private @Nullable Result currentResult;
  private @Nullable Instant timeUpAt;
  private final Map<String, SidedEntry> seen = new HashMap<>();
  private final Set<String> cancelled = new HashSet<>();
  private final List<SenshuRecord> senshuRecords = new ArrayList<>();
  private final Set<String> senshuInFinalWindow = new HashSet<>();
  private final List<GameWarning> warnings = new ArrayList<>();
  private int lastOrder;

  private GameFold(Settings settings) {
    this.settings = settings;
    this.mainClock = ClockState.stopped(settings.durationMs());
    for (Side side : Side.values()) {
      medicalClocks.put(side, ClockState.stopped(settings.medicalTimeMs()));
      tallies.put(side, new Tally());
    }
  }

  static GameState fold(KumiteGame game) {
    GameFold fold = new GameFold(game.settings());
    if (FormatCombination.isOdd(game.format(), game.isMedalGame(), game.isExtraGame())) {
      fold.warnings.add(new GameWarning(Warning.FORMAT_COMBINATION, null));
    }
    game.entriesInOrder().forEach(fold::apply);
    return fold.state();
  }

  private void apply(SidedEntry placed) {
    Entry entry = placed.entry();
    lastOrder = Math.max(lastOrder, entry.meta().order());
    if (seen.putIfAbsent(entry.id(), placed) != null) {
      skip(entry);
      return;
    }
    Side side = placed.side();
    switch (entry) {
      case Score score when side != null -> tally(side).addScore(score, settings);
      case Penalty penalty when side != null -> tally(side).addPenalty(penalty);
      case Score score -> skip(score);
      case Penalty penalty -> skip(penalty);
      case Assignment assignment -> assign(assignment);
      case ClockAction action -> clock(action);
      case SenshuRecord record -> senshu(record);
      case Amendment amendment -> amend(amendment);
      case Result result -> {
        currentResult = result;
        status = GameStatus.DECIDED;
      }
    }
  }

  /** Every side has a tally from construction on. */
  private Tally tally(Side side) {
    return Objects.requireNonNull(tallies.get(side));
  }

  private void assign(Assignment assignment) {
    String athleteId = assignment.athleteId();
    String refereeId = assignment.refereeId();
    Side side = assignment.side();
    PanelRole role = assignment.role();
    AssignmentKind kind = assignment.kind();
    if (kind == AssignmentKind.ATHLETE && side != null && athleteId != null) {
      tally(side).athleteId = athleteId;
    } else if (kind == AssignmentKind.PANEL_ADD && refereeId != null && role != null) {
      panel.put(refereeId, role);
    } else if (kind == AssignmentKind.PANEL_REMOVE && refereeId != null) {
      panel.remove(refereeId);
    } else {
      skip(assignment);
    }
  }

  private void clock(ClockAction action) {
    Long amount = action.amountMs();
    if (action.action().hasAmount() && amount == null) {
      skip(action);
      return;
    }
    long amountMs = amount == null ? 0 : amount;
    Instant at = action.meta().recordedAt();
    if (action.timer() == Timer.MEDICAL) {
      Side side = action.side();
      if (side == null) {
        skip(action);
        return;
      }
      medicalClocks.put(
          side,
          Objects.requireNonNull(medicalClocks.get(side))
              .after(
                  action.action(), at, action.timeShownMs(), amountMs, settings.medicalTimeMs()));
      return;
    }
    boolean wasRunning = mainClock.running();
    mainClock =
        mainClock.after(action.action(), at, action.timeShownMs(), amountMs, settings.durationMs());
    if (action.action() == ClockActionKind.START && !wasRunning) {
      exchange++;
    }
    moveStatus(action.action(), at);
  }

  /**
   * Only the main clock moves the status, and never out of DECIDED: only a result ends a game. Time
   * put back after zero returns to FIGHTING only if the clock then has time left, so SET_TIME to 0
   * stays TIME_UP.
   */
  private void moveStatus(ClockActionKind action, Instant at) {
    if (status == GameStatus.NOT_STARTED && action == ClockActionKind.START) {
      status = GameStatus.FIGHTING;
    } else if (status == GameStatus.FIGHTING && action == ClockActionKind.TIME_UP) {
      status = GameStatus.TIME_UP;
      timeUpAt = at;
    } else if (status == GameStatus.TIME_UP
        && action.isPuttingTimeBack()
        && mainClock.remainingAt(at) > 0) {
      status = GameStatus.FIGHTING;
      timeUpAt = null;
    }
  }

  /**
   * Kept for the end of the fold, where SENSHU is replayed from the records still standing. Whether
   * it came in the final window is decided here, where the main clock at its order is known.
   */
  private void senshu(SenshuRecord record) {
    senshuRecords.add(record);
    if (SenshuFold.inFinalWindow(
        record.exchange(),
        record.clockRemainingMs(),
        exchange == 0 ? null : exchange,
        mainClock,
        record.meta().recordedAt(),
        settings)) {
      senshuInFinalWindow.add(record.id());
    }
  }

  private void amend(Amendment amendment) {
    SidedEntry target = seen.get(amendment.targetId());
    if (target == null) {
      skip(amendment);
      return;
    }
    if (amendment.kind() == AmendmentKind.ACKNOWLEDGE) {
      return;
    }
    if (cancelled.contains(amendment.targetId())) {
      skip(amendment);
      return;
    }
    Side targetSide = target.side();
    switch (target.entry()) {
      case Score score when targetSide != null -> tally(targetSide).removeScore(score, settings);
      case Penalty penalty when targetSide != null -> tally(targetSide).removePenalty(penalty);
      case SenshuRecord _ -> {
        // SENSHU is replayed from the standing records when the fold ends.
      }
      default -> {
        skip(amendment);
        return;
      }
    }
    cancelled.add(amendment.targetId());
  }

  private void skip(Entry entry) {
    warnings.add(new GameWarning(Warning.HISTORY_INCONSISTENT, entry.id()));
  }

  private GameState state() {
    SenshuFold.Replayed senshu =
        SenshuFold.replay(
            senshuRecords,
            cancelled::contains,
            record -> senshuInFinalWindow.contains(record.id()));
    senshu
        .conflicting()
        .forEach(id -> warnings.add(new GameWarning(Warning.HISTORY_INCONSISTENT, id)));
    Map<Side, SideState> sides = new EnumMap<>(Side.class);
    tallies.forEach((side, tally) -> sides.put(side, tally.state()));
    List<PanelMember> members = new ArrayList<>();
    panel.forEach((refereeId, role) -> members.add(new PanelMember(refereeId, role)));
    return new GameState(
        status,
        mainClock,
        medicalClocks,
        exchange,
        sides,
        members,
        currentResult,
        status == GameStatus.TIME_UP ? timeUpAt : null,
        senshu.state(),
        cancelled,
        warnings,
        lastOrder);
  }

  /** One side's running counts. Cancelling takes back exactly what adding gave. */
  private static final class Tally {

    private @Nullable String athleteId;
    private int total;
    private int ippon;
    private int wazaAri;
    private int chui;
    private int hansokuChui;

    void addScore(Score score, Settings settings) {
      countScore(score, settings, 1);
    }

    void removeScore(Score score, Settings settings) {
      countScore(score, settings, -1);
    }

    void addPenalty(Penalty penalty) {
      countPenalty(penalty, 1);
    }

    void removePenalty(Penalty penalty) {
      countPenalty(penalty, -1);
    }

    private void countScore(Score score, Settings settings, int sign) {
      total += sign * settings.pointsFor(score.level());
      if (score.level() == ScoreLevel.IPPON) {
        ippon += sign;
      } else if (score.level() == ScoreLevel.WAZA_ARI) {
        wazaAri += sign;
      }
    }

    private void countPenalty(Penalty penalty, int sign) {
      if (penalty.level() == PenaltyLevel.CHUI) {
        chui += sign;
      } else if (penalty.level() == PenaltyLevel.HANSOKU_CHUI) {
        hansokuChui += sign;
      }
    }

    SideState state() {
      return new SideState(athleteId, total, ippon, wazaAri, chui, hansokuChui);
    }
  }
}
