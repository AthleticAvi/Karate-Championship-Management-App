package com.management.kumitegame.web;

/**
 * The two views of a game, as JSON views: one response record serves both, and a field marked for
 * the scorekeeper is left out of the public view.
 *
 * <p>A handler that names no view writes every field, so the scorekeeper's view, every mutation and
 * the game in a 409 need no marking.
 */
final class GameViews {

  /** What anyone may follow: the game without who recorded what, warnings or notes. */
  interface Public {}

  /** Everything, for a Scorekeeper or an Admin. */
  interface Scorekeeper extends Public {}

  private GameViews() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }
}
