/**
 * Unmarked on purpose. On a {@code @NullMarked} package, Spring Data adds a runtime null check to
 * every repository: a finder returning nothing throws instead of returning null. Marking this
 * package is a behaviour change, so it is decided on its own, not as part of a null annotation.
 */
@NullUnmarked
package com.management.repositories;

import org.jspecify.annotations.NullUnmarked;
