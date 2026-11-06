package com.management.dto;

import com.management.enums.AccountRole;

/** An account as the API reports it. Never a password or a hash. */
public record AccountResponse(String username, AccountRole role) {}
