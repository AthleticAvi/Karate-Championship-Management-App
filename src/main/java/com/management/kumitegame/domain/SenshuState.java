package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/**
 * SENSHU, derived from the standing SENSHU records: never stored.
 *
 * @param holder the side holding it; one side or none, so both can never hold it
 * @param closed withdrawn in the final window, so nobody should earn it for the rest of the game
 *     (Article 12.2.10)
 */
public record SenshuState(@Nullable Side holder, boolean closed) {

  public boolean isHeldBy(Side side) {
    return holder == side;
  }
}
