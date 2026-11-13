package com.management.kumitegame.web;

import com.management.kumitegame.domain.ClockActionKind;
import com.management.kumitegame.domain.PauseReason;
import com.management.kumitegame.domain.Side;
import com.management.kumitegame.domain.Timer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A press on the main clock, or on a medical timer.
 *
 * @param id made by the client, one per action, and sent again on a retry of that action
 * @param side the medical timer's side; left out for the main clock
 * @param timeShownMs what the screen showed when the button was pressed
 * @param amountMs the time added or set; only for ADD_TIME and SET_TIME
 * @param expectedVersion the game's {@code version} a SET_TIME was chosen against; required for
 *     SET_TIME, refused on any other action, which is appended
 */
public record ClockActionRequest(
    @NotBlank @Size(max = 64) String id,
    @NotNull Timer timer,
    @Nullable Side side,
    @NotNull ClockActionKind action,
    @NotNull @PositiveOrZero Long timeShownMs,
    @Nullable Long amountMs,
    @Nullable PauseReason pauseReason,
    @Schema(
            description =
                "Required for SET_TIME: the game's version the screen showed. A stale one answers"
                    + " 409 with the game as it now stands. Refused on any other action")
        @Nullable Long expectedVersion) {}
