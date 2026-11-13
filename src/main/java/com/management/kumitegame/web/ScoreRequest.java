package com.management.kumitegame.web;

import com.management.kumitegame.domain.ScoreLevel;
import com.management.kumitegame.domain.ScoreSource;
import com.management.kumitegame.domain.Side;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A score.
 *
 * @param id made by the client, one per action, and sent again on a retry of that action
 * @param exchange an earlier exchange a video review names; left out, the current one. One that has
 *     not happened yet is refused
 * @param clockRemainingMs the time the screen showed when the score was given
 */
public record ScoreRequest(
    @NotBlank @Size(max = 64) String id,
    @NotNull Side side,
    @NotNull ScoreLevel level,
    @NotNull ScoreSource source,
    @Nullable @Positive Integer exchange,
    @Nullable @PositiveOrZero Long clockRemainingMs) {}
