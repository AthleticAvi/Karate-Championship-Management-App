package com.management.kumitegame.web;

import com.management.kumitegame.domain.GameFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A new game: its format, the athlete on each side and the panel. Its settings come from
 * configuration, and change through the settings resource before the start.
 */
public record KumiteGameRequest(
    @NotNull GameFormat format,
    boolean medalGame,
    boolean extraGame,
    @NotBlank @Size(max = 64) String akaAthleteId,
    @NotBlank @Size(max = 64) String aoAthleteId,
    @Nullable @Size(max = 20) List<@Valid @NotNull PanelMemberRequest> panel) {}
