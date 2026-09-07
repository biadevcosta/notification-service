package com.biadevcosta.notification.application.port;

import java.time.LocalDateTime;

/**
 * Remembers which reminders were already handled, so a redelivered message is a no-op.
 * Keyed by {@code appointmentId} — a business id, independent of the transport.
 */
public interface ProcessedReminderStore {

    boolean isProcessed(String appointmentId);

    void markProcessed(String appointmentId, LocalDateTime processedAt);
}
