package com.management.mappers;

import com.management.dto.KumiteGameResponse;
import com.management.dto.PlayerSummary;
import com.management.enums.PlayerColor;
import com.management.models.KumiteGame;
import com.management.models.Player;
import com.management.models.Referee;
import java.util.List;

/**
 * Turns a stored match into the match the API reports.
 *
 * <p>This is the boundary the epic exists to create. Nothing on the far side of it is a persistence
 * type, so a field added to {@link KumiteGame} — orchestrator state, audit columns, scheduling data
 * — joins the public contract only when someone adds it here on purpose.
 *
 * <p>Hand-written rather than generated. Two response types with seven fields between them do not
 * justify an annotation processor, and adding one is a project-wide decision rather than something
 * a single change adopts (see {@code workflow/standards/java.md}).
 *
 * <p>Static rather than a bean, so the slice tests exercise the real mapping instead of importing
 * one more thing into every web-slice context.
 */
public final class KumiteGameMapper {

  private KumiteGameMapper() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  public static KumiteGameResponse toResponse(KumiteGame game) {
    return new KumiteGameResponse(
        game.getId(),
        game.getGameState(),
        (int) game.getRemainingTime().toSeconds(),
        toSummary(fighter(game, PlayerColor.RED)),
        toSummary(fighter(game, PlayerColor.BLUE)),
        game.getReferees().stream().map(Referee::name).toList(),
        game.getWinner());
  }

  private static PlayerSummary toSummary(Player player) {
    return new PlayerSummary(
        player.getId(),
        player.getName(),
        player.getPoints().getNumOfPoints(),
        player.getFouls().getNumOfFouls());
  }

  /**
   * A match has exactly one RED fighter and exactly one BLUE fighter — a settled domain rule, not a
   * request-time option. A stored match missing one is data that violates the system's own
   * invariant, which is a server fault and reported as one. The guard that stops such a match being
   * created in the first place is #28.
   */
  private static Player fighter(KumiteGame game, PlayerColor color) {
    List<PlayerColor> present = List.copyOf(game.getPlayersMap().keySet());
    Player player = game.getPlayersMap().get(color);
    if (player == null) {
      throw new IllegalStateException(
          "Stored match " + game.getId() + " has no " + color + " fighter; it holds " + present);
    }
    return player;
  }
}
