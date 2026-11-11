package com.management.kumitegame.domain;

import java.util.List;

/** What a new game is created with. Its settings come from configuration, not from here. */
public record NewGame(
    GameFormat format,
    boolean medalGame,
    boolean extraGame,
    String akaAthleteId,
    String aoAthleteId,
    List<PanelMember> panel) {

  public NewGame {
    panel = List.copyOf(panel);
  }
}
