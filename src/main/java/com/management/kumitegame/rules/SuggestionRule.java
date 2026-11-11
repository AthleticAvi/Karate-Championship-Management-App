package com.management.kumitegame.rules;

import com.management.kumitegame.domain.Standing;
import com.management.kumitegame.domain.SuggestedResult;
import java.util.Optional;

/**
 * One step of the suggested-result chain: a way the rules decide a game.
 *
 * <p>Each step is a bean ordered with {@code @Order}, so the container sorts the list the chain is
 * given. A new step is a new class with an order value, and no existing step changes. The first
 * step that answers decides; empty means this step has nothing to say, and the next is asked.
 *
 * <p>A step reads only the standing and the game's settings snapshot, never configuration, so a
 * game keeps the rules it was created with.
 */
public interface SuggestionRule {

  /** The result this step decides, or empty when it has nothing to say. */
  Optional<SuggestedResult> suggest(Standing standing);
}
