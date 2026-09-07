package com.biadevcosta.notification.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcProcessedReminderStoreTest {

    @Mock
    ProcessedReminderJdbcRepository jdbc;

    private JdbcProcessedReminderStore store() {
        return new JdbcProcessedReminderStore(jdbc);
    }

    @Test
    void isProcessed_delegatesToExistsById() {
        when(jdbc.existsById("apt-1")).thenReturn(true);

        assertThat(store().isProcessed("apt-1")).isTrue();
    }

    @Test
    void markProcessed_savesTheGivenAppointmentAndTimestamp() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);

        store().markProcessed("apt-1", now);

        ArgumentCaptor<ProcessedReminderEntity> captor = ArgumentCaptor.forClass(ProcessedReminderEntity.class);
        verify(jdbc).save(captor.capture());
        assertThat(captor.getValue().getAppointmentId()).isEqualTo("apt-1");
        assertThat(captor.getValue().getProcessedAt()).isEqualTo(now);
    }

    @Test
    void markProcessed_swallowsDuplicateKey() {
        when(jdbc.save(any())).thenThrow(new DuplicateKeyException("PK clash"));

        assertThatCode(() -> store().markProcessed("apt-1", LocalDateTime.now()))
                .doesNotThrowAnyException();
    }
}
