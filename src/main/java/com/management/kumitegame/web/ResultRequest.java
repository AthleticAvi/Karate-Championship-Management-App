package com.management.kumitegame.web;

import com.management.kumitegame.domain.Outcome;
import com.management.kumitegame.domain.ResultReason;
import com.management.kumitegame.domain.Side;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A result, as the officials decided it. Only a WIN names a winner.
 *
 * @param id made by the client, one per action, and sent again on a retry of that action
 * @param reasonDetail such as the 10-second rule on a KIKEN
 * @param bothDisqualified keeps the fact in a medal game decided on points
 */
public record ResultRequest(
    @NotBlank @Size(max = 64) String id,
    @NotNull Outcome outcome,
    @Nullable Side winner,
    @NotNull ResultReason reason,
    @Nullable @Size(max = 200) String reasonDetail,
    boolean bothDisqualified) {}
