package com.management.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Which browser origins may call the API, bound from {@code cors.*} and validated at startup.
 *
 * <p>Each value is one exact origin: a scheme, a host and an optional port. A wildcard, a path or a
 * trailing slash stops startup, because an origin that nearly matches looks correct and refuses
 * every call.
 *
 * @param allowedOrigins the origins that may call the API. Empty refuses every other origin
 */
@Validated
@ConfigurationProperties("cors")
public record CorsProperties(
    @NotNull
        List<
                @Pattern(
                    regexp = "https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?",
                    message = "must be one exact origin, such as https://scores.example")
                String>
            allowedOrigins) {}
