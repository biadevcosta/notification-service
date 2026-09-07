package com.biadevcosta.notification.infrastructure.persistence;

import com.biadevcosta.notification.application.port.ProcessedReminderStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/** {@link ProcessedReminderStore} backed by Spring Data JDBC (table {@code processed_reminders}). */
@Repository
public class JdbcProcessedReminderStore implements ProcessedReminderStore {

    private final ProcessedReminderJdbcRepository jdbc;

    public JdbcProcessedReminderStore(ProcessedReminderJdbcRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isProcessed(String appointmentId) {
        return jdbc.existsById(appointmentId);
    }

    @Override
    public void markProcessed(String appointmentId, LocalDateTime processedAt) {
        ProcessedReminderEntity entity = new ProcessedReminderEntity();
        entity.setAppointmentId(appointmentId);
        entity.setProcessedAt(processedAt);
        try {
            jdbc.save(entity);
        } catch (DataIntegrityViolationException | DbActionExecutionException e) {
            // a concurrent handler already recorded this reminder — idempotent, nothing to do
        }
    }
}
