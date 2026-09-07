package com.biadevcosta.notification.application.command;

import java.time.LocalDateTime;

/**
 * Input to {@code SendAppointmentReminderUseCase}. The inbound adapter (RabbitMQ listener, or any
 * other transport) maps its message onto this — the use case never sees a queue payload type.
 */
public record SendReminderCommand(String appointmentId, String patientId, LocalDateTime scheduledAt) {
}
