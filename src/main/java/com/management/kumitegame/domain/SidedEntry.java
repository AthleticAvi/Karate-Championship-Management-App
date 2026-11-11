package com.management.kumitegame.domain;

import org.jspecify.annotations.Nullable;

/** An entry with the side whose list holds it: only scores and penalties sit on a side. */
record SidedEntry(Entry entry, @Nullable Side side) {}
