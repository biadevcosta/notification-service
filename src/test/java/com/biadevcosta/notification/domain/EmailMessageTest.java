package com.biadevcosta.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailMessageTest {

    @Test
    void appointmentReminder_addressesThePatient_andMentionsTheDate() {
        PatientContact patient = new PatientContact("pat-1", "John Doe", "john@example.com");

        EmailMessage email = EmailMessage.appointmentReminder(patient, LocalDateTime.of(2030, 12, 1, 10, 30));

        assertThat(email.toEmail()).isEqualTo("john@example.com");
        assertThat(email.toName()).isEqualTo("John Doe");
        assertThat(email.subject()).contains("01/12/2030 às 10:30");
        assertThat(email.htmlBody()).contains("John Doe").contains("01/12/2030 às 10:30");
    }

    @Test
    void rejectsBlankFields() {
        assertThatThrownBy(() -> new EmailMessage("", "John", "subject", "<p>body</p>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailMessage("john@example.com", "John", " ", "<p>body</p>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailMessage("john@example.com", "John", "subject", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
