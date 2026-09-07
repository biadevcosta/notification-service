CREATE TABLE processed_reminders (
    appointment_id VARCHAR(36) NOT NULL,   -- idempotency key: one reminder per appointment creation
    processed_at   DATETIME    NOT NULL,
    CONSTRAINT pk_processed_reminders PRIMARY KEY (appointment_id)
);
