package com.biadevcosta.notification.infrastructure.persistence;

import org.springframework.data.repository.CrudRepository;

/** Spring Data JDBC repository over {@link ProcessedReminderEntity}. */
public interface ProcessedReminderJdbcRepository extends CrudRepository<ProcessedReminderEntity, String> {
}
