package com.management.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The request to create a match.
 *
 * <p>The constraints reject a malformed request at the boundary as a 400 naming every failing
 * field; what they cannot see — that the two fighters carry one RED and one BLUE — stays a domain
 * rule in {@code KumiteGameService}, because it reads the colour <em>values</em>, not the shape.
 *
 * @param playersMap exactly two fighters, keyed by a label the server ignores — the authoritative
 *     colour is each fighter's own {@code color} field
 * @param refereeList at least one referee name, none blank
 * @param gameDuration seconds, optional; must be positive, and a positive value outside the
 *     configured set falls back to the configured default
 */
public record KumiteGameRequestDTO(
    @NotNull(message = "a match needs fighters")
        @Size(min = 2, max = 2, message = "a match fields exactly two fighters")
        Map<String, @NotNull(message = "a fighter must not be null") @Valid PlayerDTO> playersMap,
    @NotEmpty(message = "a match needs at least one referee")
        List<@NotBlank(message = "a referee name must not be blank") String> refereeList,
    @Positive(message = "must be a positive number of seconds") @Nullable Integer gameDuration) {}
