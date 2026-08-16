package com.management.dto;

/**
 * A fighter as the API reports them, standalone.
 *
 * <p>Identical in shape to {@link PlayerSummary}, and deliberately a separate type: this one is the
 * body of {@code /api/players/{id}} and {@link PlayerSummary} is a fragment of a match. They are
 * free to diverge — a standalone fighter will want a record across matches, a match fragment will
 * not — and collapsing them now would couple two contracts that have no reason to move together.
 */
public record PlayerResponse(String id, String name, int points, int fouls) {}
