package com.management.dto;

import com.management.enums.PlayerColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A referee's decision to set the result themselves.
 *
 * <p>The reason is required by a constraint rather than by a service check, so an override with no
 * justification is refused at the boundary — an override is an exception to the rules, and the
 * reason is the only record of why it was made.
 *
 * @param winner the colour to declare, or {@code null} to declare a draw
 * @param reason why the rules engine's result is being overruled; required
 * @param referee the referee who made the call, who may hold no account. Who recorded it comes from
 *     the signed-in account, so a caller cannot supply that
 */
public record WinnerOverrideRequest(
    @Schema(implementation = PlayerColor.class) @Nullable String winner,
    @NotBlank(message = "an override must record why it was made") @Size(max = 500) String reason,
    @NotBlank(message = "an override must record which referee made the call") @Size(max = 100)
        String referee) {}
