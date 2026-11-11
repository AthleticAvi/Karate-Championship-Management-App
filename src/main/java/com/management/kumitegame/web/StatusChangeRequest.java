package com.management.kumitegame.web;

import com.management.kumitegame.domain.StatusChangeKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Approve, reopen or void a game.
 *
 * @param id made by the client, one per action, and sent again on a retry of that action
 * @param reason required to reopen an APPROVED game and to void one, both an Admin's moves
 */
public record StatusChangeRequest(
    @NotBlank @Size(max = 64) String id,
    @NotNull StatusChangeKind kind,
    @Nullable @Size(max = 500) String reason) {}
