package com.management.dto;

import com.management.enums.GameState;
import com.management.enums.PlayerColor;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A match as the API reports it: everything a scoreboard needs to render, and nothing else.
 *
 * <p><strong>Colour vocabulary, not positions.</strong> {@code red} and {@code blue} name the two
 * roles the rest of the codebase, the WKF rulebook and the stored document all use. The shape this
 * replaces described a match as {@code player1} / {@code player2}, which mapped onto nothing and
 * forced every consumer to learn a private convention.
 *
 * <p><strong>Named fields rather than a map keyed by colour.</strong> A map serialises to a JSON
 * object whose keys a client has to know anyway, so it buys symmetry with the stored form at the
 * cost of self-documentation. Chosen deliberately; the alternative was defensible.
 *
 * <p><strong>Absence of a winner is {@code null}</strong>, never a sentence. The field is always
 * present so a client can test it directly rather than checking for a missing key, and it is a
 * colour rather than a display string so the client renders the sentence in its own language.
 *
 * <p><strong>{@code remainingSeconds} is a whole number of seconds</strong>, not a duration object.
 * See {@code workflow/patterns/service-exposure.md}: a field whose format cannot be misread needs
 * no format policy. {@code startTime} is deliberately absent — it is internal bookkeeping used to
 * compute elapsed time, and a client holding {@code remainingSeconds} has no use for it.
 */
public record KumiteGameResponse(
    String id,
    GameState gameState,
    int remainingSeconds,
    PlayerSummary red,
    PlayerSummary blue,
    List<String> referees,
    @Nullable PlayerColor winner) {}
