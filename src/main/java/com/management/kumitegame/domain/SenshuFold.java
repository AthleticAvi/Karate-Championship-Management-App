package com.management.kumitegame.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Derives SENSHU by replaying the standing SENSHU records in order (Article 12.2).
 *
 * <p>A cancelled record never happened, so replaying only the standing ones gives each fold rule
 * for a cancel: a cancelled AWARDED takes the holder away and closes nothing, and a cancelled
 * WITHDRAWN gives the holder back and undoes the closure it caused.
 *
 * <ul>
 *   <li>AWARDED gives SENSHU to its side. One that meets a holder cannot apply, since two sides
 *       would hold it: it is reported as conflicting, and skipped.
 *   <li>WITHDRAWN takes SENSHU from its side, and closes it when given in the final window (Article
 *       12.2.10). One whose side does not hold SENSHU changes nothing and closes nothing: it was
 *       recorded with a warning, or its AWARDED was cancelled.
 * </ul>
 */
final class SenshuFold {

  private SenshuFold() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  /**
   * SENSHU after the records, and the ids of any AWARDED that met a holder.
   *
   * @param conflicting AWARDED records skipped because a side already held SENSHU
   */
  record Replayed(SenshuState state, List<String> conflicting) {

    Replayed {
      conflicting = List.copyOf(conflicting);
    }
  }

  /**
   * Replays the records that still stand.
   *
   * @param inOrder every SENSHU record, in the order recorded
   * @param cancelled whether a CANCEL took a record back
   * @param inFinalWindow whether a record was given in the final window
   */
  static Replayed replay(
      List<SenshuRecord> inOrder,
      Predicate<String> cancelled,
      Predicate<SenshuRecord> inFinalWindow) {
    Side holder = null;
    boolean closed = false;
    List<String> conflicting = new ArrayList<>();
    for (SenshuRecord record : inOrder) {
      if (cancelled.test(record.id())) {
        continue;
      }
      if (record.kind() == SenshuRecordKind.AWARDED) {
        if (holder == null) {
          holder = record.side();
        } else {
          conflicting.add(record.id());
        }
      } else if (record.side() == holder) {
        holder = null;
        closed = closed || inFinalWindow.test(record);
      }
    }
    return new Replayed(new SenshuState(holder, closed), conflicting);
  }

  /**
   * Whether an action came in the final window: fewer than the window's milliseconds left (Articles
   * 12.2.8, 12.2.10). The time left is the clock remaining the action was sent with, the time its
   * screen showed. The main clock at that instant decides instead when it was sent with none, or
   * when the action names an earlier exchange, whose time it then carries: the window is about when
   * the action is taken. The penalty warning and the closure both decide it here.
   *
   * @param exchange the exchange the action names; empty means the current one
   * @param currentExchange the exchange open when the action is taken; empty before the start
   */
  static boolean inFinalWindow(
      @Nullable Integer exchange,
      @Nullable Long clockRemainingMs,
      @Nullable Integer currentExchange,
      ClockState mainClock,
      Instant at,
      Settings settings) {
    boolean current = exchange == null || exchange.equals(currentExchange);
    long left = current && clockRemainingMs != null ? clockRemainingMs : mainClock.remainingAt(at);
    return left < settings.finalWindowMs();
  }
}
