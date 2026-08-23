package com.management.dto;

/**
 * A fighter as a scoreboard needs to render them: who they are and what they have accumulated.
 *
 * <p>Points and fouls are flattened to bare counts rather than nested behind the {@code Points} and
 * {@code Foul} wrapper objects the database stores. Those wrappers exist so scoring strategies have
 * something to mutate; a client has no use for the distinction and should not have to learn it.
 */
public record PlayerSummary(String id, String name, int points, int fouls) {}
