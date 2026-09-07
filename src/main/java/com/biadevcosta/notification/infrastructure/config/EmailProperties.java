package com.biadevcosta.notification.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code app.email.*} — the e-mail provider endpoint, credentials and sender identity. */
@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(String apiUrl, String apiKey, String fromEmail, String fromName) {
}
