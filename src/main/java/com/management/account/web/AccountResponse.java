package com.management.account.web;

import com.management.account.domain.AccountRole;

/** An account as the API reports it. Never a password or a hash. */
public record AccountResponse(String username, AccountRole role) {}
