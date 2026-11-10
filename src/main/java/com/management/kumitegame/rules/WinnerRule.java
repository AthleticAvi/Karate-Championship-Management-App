package com.management.kumitegame.rules;

import com.management.kumitegame.domain.GameWithFighters;
import com.management.kumitegame.domain.MatchEndReason;
import com.management.kumitegame.domain.MatchOutcome;
import java.util.Optional;

/**
 * One way a winner can be decided, evaluated in priority order until one answers.
 *
 * <p>Ordered with Spring's {@code @Order}: the container sorts the injected list, so priority is
 * declared on the rule rather than encoded in a chain of {@code if} statements someone has to
 * re-read to change. A new rule is a new bean with an order value.
 *
 * <p>A rule that does not apply returns empty rather than a null winner — "no rule applied" and
 * "this rule says nobody won" are different answers, and only the second ends the search.
 */
public interface WinnerRule {

  /** The outcome this rule decides, or empty when it has nothing to say about this match. */
  Optional<MatchOutcome> decide(GameWithFighters match, MatchEndReason reason);
}
