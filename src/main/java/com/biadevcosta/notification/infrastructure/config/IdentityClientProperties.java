package com.biadevcosta.notification.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code app.identity.*} — where to reach identity-service for user lookups. */
@ConfigurationProperties(prefix = "app.identity")
public record IdentityClientProperties(String baseUrl) {
}
