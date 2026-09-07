package com.biadevcosta.notification.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Row in {@code processed_reminders}. Insert-only: {@link #isNew()} is always {@code true} and a
 * repeated insert (PK clash) is swallowed by {@code JdbcProcessedReminderStore} as "already done".
 */
@Table("processed_reminders")
public class ProcessedReminderEntity implements Persistable<String> {

    @Id
    private String appointmentId;
    private LocalDateTime processedAt;

    public String getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(String appointmentId) {
        this.appointmentId = appointmentId;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    @Override
    public String getId() {
        return appointmentId;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
