package com.management.kumitegame.domain;

/** Anything recorded on a game. Read by {@code meta().order()}, the entries tell the game. */
public sealed interface Entry permits Score, Penalty, Assignment, ClockAction, Amendment, Result {

  EntryMeta meta();

  default String id() {
    return meta().id();
  }
}
