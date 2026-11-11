package com.management.kumitegame.domain;

import java.util.Optional;

/**
 * The rules that read where a game stands and point to its result: a port the domain owns and the
 * rules package implements, so the domain never depends on the rules. See {@code
 * src/main/java/com/management/kumitegame/rules/README.md}.
 *
 * <p>Asked every time a game is folded, so a read, a recorded result and a correction all see the
 * same answer. It must answer for any standing, never throw: the fold never throws.
 */
@FunctionalInterface
public interface ResultRules {

  /** The result the rules point to, or empty while they point to none. */
  Optional<SuggestedResult> suggest(Standing standing);
}
