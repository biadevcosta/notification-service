package com.biadevcosta.notification.infrastructure.messaging;

import java.time.LocalDateTime;

/**
 * Wire format of the RabbitMQ reminder — this service's own copy of the record. The producer
 * (scheduling-service) uses a class with the same shape; the JSON converter is configured to
 * deserialize by this type, not by the type header the producer adds.
 */
public record AppointmentReminderMessage(String appointmentId, String patientId, LocalDateTime scheduledAt) {
}
