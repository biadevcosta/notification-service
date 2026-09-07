package com.biadevcosta.notification.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code app.reminder.rabbit.*} — the reminder topology, shared with scheduling-service. */
@ConfigurationProperties(prefix = "app.reminder.rabbit")
public record ReminderRabbitProperties(String exchange, String queue, String routingKey, String dlq) {
}
