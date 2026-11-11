package com.management.kumitegame.web;

import com.management.kumitegame.domain.PanelRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A referee on a new game's panel, in a role. */
public record PanelMemberRequest(
    @NotBlank @Size(max = 64) String refereeId, @NotNull PanelRole role) {}
