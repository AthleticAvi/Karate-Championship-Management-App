package com.management.kumitegame.web;

import com.management.kumitegame.domain.Infraction;
import com.management.kumitegame.domain.PenaltyLevel;
import com.management.kumitegame.domain.Side;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A penalty, at the level the referee gave.
 *
 * @param id made by the client, one per action, and sent again on a retry of that action
 * @param exchange an earlier exchange a video review names; empty means the current one. One that
 *     has not happened yet is refused
 */
public record PenaltyRequest(
    @NotBlank @Size(max = 64) String id,
    @NotNull Side side,
    @NotNull PenaltyLevel level,
    @Nullable Infraction infraction,
    @Nullable @Positive Integer exchange,
    @Nullable @PositiveOrZero Long clockRemainingMs) {}
