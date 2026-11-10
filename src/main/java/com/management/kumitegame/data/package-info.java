/**
 * Unmarked on purpose. On a {@code @NullMarked} package, Spring Data adds a runtime null check to
 * every repository. Nothing here depends on it yet, but it is a behaviour change, so it is decided
 * on its own. A custom finder would make it matter: see the README beside this file.
 */
@NullUnmarked
package com.management.kumitegame.data;

import org.jspecify.annotations.NullUnmarked;
