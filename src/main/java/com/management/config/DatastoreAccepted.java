package com.management.config;

/**
 * The datastore answered and passed {@link DatastoreGuaranteeCheck}, so it is safe to use.
 *
 * <p>Published at startup before the port opens, or later if it first answers then. A listener that
 * throws {@link IllegalStateException} stops the application either way.
 */
public record DatastoreAccepted() {}
