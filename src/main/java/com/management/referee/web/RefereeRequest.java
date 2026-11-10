package com.management.referee.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body of a create and a rename: the referee's name. */
public record RefereeRequest(
    @NotBlank(message = "a referee needs a name") @Size(max = 100) String name) {}
