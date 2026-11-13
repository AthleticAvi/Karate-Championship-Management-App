package com.management.kumitegame.web;

import com.management.kumitegame.domain.SenshuRecordKind;
import com.management.kumitegame.domain.Side;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * SENSHU awarded to a side or withdrawn from it, as the officials decided.
 *
 * @param id made by the client, one per action, and sent again on a retry of that action
 * @param reason why, such as a video review
 * @param exchange an earlier exchange a video review names; empty means the current one. One that
 *     has not happened yet is refused
 */
public record SenshuRecordRequest(
    @NotBlank @Size(max = 64) String id,
    @NotNull Side side,
    @NotNull SenshuRecordKind kind,
    @Nullable @Size(max = 500) String reason,
    @Nullable @Positive Integer exchange,
    @Nullable @PositiveOrZero Long clockRemainingMs) {}
