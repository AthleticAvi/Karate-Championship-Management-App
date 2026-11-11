package com.management.kumitegame.web;

import com.management.kumitegame.domain.AmendmentKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A correction: cancel a score or penalty, or acknowledge the warnings on an entry.
 *
 * @param id made by the client, one per action, and sent again on a retry of that action
 * @param targetId the entry it corrects
 */
public record AmendmentRequest(
    @NotBlank @Size(max = 64) String id,
    @NotNull AmendmentKind kind,
    @NotBlank @Size(max = 64) String targetId,
    @Nullable @Size(max = 500) String reason) {}
