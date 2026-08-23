package com.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A fighter inside a match-creation request.
 *
 * <p>The colour is constrained only as present; whether it names a real colour is decided against
 * {@code PlayerColor} in the service, where the failure carries its own exception type and 400
 * mapping. The {@code id} field the class-based version carried is gone: it was never read.
 */
public record PlayerDTO(
    @NotBlank(message = "a fighter needs a name") @Size(max = 100) String name,
    @NotBlank(message = "a fighter needs a colour") String color) {}
