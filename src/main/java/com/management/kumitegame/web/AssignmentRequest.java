package com.management.kumitegame.web;

import com.management.kumitegame.domain.AssignmentKind;
import com.management.kumitegame.domain.PanelRole;
import com.management.kumitegame.domain.Side;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * An athlete put on a side, or a referee added to or removed from the panel. ATHLETE names a side
 * and an athlete, PANEL_ADD a referee and a role, PANEL_REMOVE a referee.
 *
 * @param id made by the client, one per action, and sent again on a retry of that action
 */
public record AssignmentRequest(
    @NotBlank @Size(max = 64) String id,
    @NotNull AssignmentKind kind,
    @Nullable Side side,
    @Nullable @Size(max = 64) String athleteId,
    @Nullable @Size(max = 64) String refereeId,
    @Nullable PanelRole role) {}
