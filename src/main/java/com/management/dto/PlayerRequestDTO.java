package com.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The request to create a standalone fighter. */
public record PlayerRequestDTO(
    @NotBlank(message = "a fighter needs a name") @Size(max = 100) String name) {}
