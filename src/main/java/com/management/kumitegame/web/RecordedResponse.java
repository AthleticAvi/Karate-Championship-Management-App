package com.management.kumitegame.web;

import com.management.kumitegame.domain.Warning;
import java.util.List;

/**
 * What a mutation answers: the game as it now stands, so a screen needs no second read, and the
 * warnings the action raised. A retried action answers the warnings stored with the first.
 */
public record RecordedResponse(KumiteGameResponse game, List<Warning> raisedWarnings) {}
