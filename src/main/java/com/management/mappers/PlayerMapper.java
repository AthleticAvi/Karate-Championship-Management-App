package com.management.mappers;

import com.management.dto.PlayerResponse;
import com.management.models.Player;

/**
 * Turns a stored fighter into the fighter the API reports.
 *
 * <p>Flattens the {@code Points} and {@code Foul} wrappers to bare counts. Those types exist so the
 * scoring strategies have something to mutate in place; the nesting is an implementation detail of
 * scoring and was previously visible to every caller of {@code GET /api/players/{id}}.
 */
public final class PlayerMapper {

  private PlayerMapper() {
    throw new IllegalStateException("Utility class cannot be instantiated");
  }

  public static PlayerResponse toResponse(Player player) {
    return new PlayerResponse(
        player.getId(),
        player.getName(),
        player.getPoints().getNumOfPoints(),
        player.getFouls().getNumOfFouls());
  }
}
