package com.management.kumitegame.domain;

/**
 * A score's level (Article 8). Its points come from the game's settings, never from here.
 *
 * <p>The rulebook mostly writes WAZA ARI. A constant cannot hold a space, so the code writes
 * WAZA_ARI.
 */
public enum ScoreLevel {
  IPPON,
  WAZA_ARI,
  YUKO
}
