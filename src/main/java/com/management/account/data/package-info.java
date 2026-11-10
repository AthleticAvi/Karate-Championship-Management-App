/**
 * Unmarked on purpose. On a {@code @NullMarked} package, Spring Data adds a runtime null check to
 * every repository. Nothing here depends on it yet, but it is a behaviour change, so it is decided
 * on its own. A custom finder would make it matter: see {@code
 * src/main/java/com/management/kumitegame/data/README.md}.
 */
@NullUnmarked
package com.management.account.data;

import org.jspecify.annotations.NullUnmarked;
