package com.management.kumitegame.rules;

import com.management.kumitegame.domain.GameFormat;
import com.management.kumitegame.domain.Standing;
import com.management.kumitegame.domain.SuggestedResult;

/**
 * Where the chain differs between the formats, and only there: a game still level at time up. One
 * bean per format, which {@link LevelRule} picks by the game's format.
 */
public interface FormatRules {

  GameFormat format();

  /** The suggestion at time up when points, SENSHU, IPPON and WAZA_ARI are all level. */
  SuggestedResult whenLevel(Standing standing);
}
