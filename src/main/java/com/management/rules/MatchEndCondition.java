package com.management.rules;

import com.management.enums.MatchEndReason;
import com.management.models.GameWithFighters;
import java.util.Optional;

/**
 * One reason a match might be over, evaluated on its own.
 *
 * <p>Each condition is a separate bean, discovered by the container and collected into a list, so a
 * new rule — a per-championship variation, a rulebook revision — arrives as a new class and changes
 * none of the existing ones. That is the extensibility constraint epic #68 carries throughout, and
 * it mirrors how {@code PointsType} already attaches a strategy per constant.
 *
 * <p>Implementations must be cheap and free of side effects: this runs after every point and every
 * foul, and returning a reason must not itself end anything. Ending is the caller's job, so that
 * ordering, idempotency and persistence are decided in one place.
 *
 * <p><strong>Every implementation declares an {@code @Order}</strong>, because more than one can be
 * true at the same instant — a fighter at the foul limit is also disqualified once the stage
 * escalates that far, and a point can cross the winning score on an already-expired clock. The
 * first to answer supplies the recorded {@code endReason} <em>and</em> the reason handed to the
 * winner rules, so leaving it to bean-discovery order would let the classpath decide what the
 * result says. The declared order is most-specific first: disqualification (10), foul limit (20),
 * points (30), time expiry (40).
 */
public interface MatchEndCondition {

  /** The reason this condition ends the match, or empty when it does not apply. */
  Optional<MatchEndReason> evaluate(GameWithFighters match);
}
