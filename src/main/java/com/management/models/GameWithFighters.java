package com.management.models;

import com.management.enums.PlayerColor;
import java.util.Map;

/**
 * A match composed with its fighters, for reads that need both aggregates.
 *
 * <p>The match stores only fighter ids; names and scores live on the {@link Player} documents.
 * Anything that renders a match — the response mapper above all — needs both, and this record is
 * that composition, built in the application service per {@code
 * workflow/patterns/service-interaction.md}: a cross-aggregate <em>read</em>, deliberately not a
 * second stored copy.
 */
public record GameWithFighters(KumiteGame game, Map<PlayerColor, Player> fighters) {}
