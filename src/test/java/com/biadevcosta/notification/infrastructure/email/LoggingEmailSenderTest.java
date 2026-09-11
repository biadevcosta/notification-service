package com.biadevcosta.notification.infrastructure.email;

import com.biadevcosta.notification.domain.EmailMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class LoggingEmailSenderTest {

    @Test
    void send_logsAndNeverThrows() {
        EmailMessage message = new EmailMessage("john@example.com", "John Doe", "Subject", "<p>body</p>");

        assertThatCode(() -> new LoggingEmailSender().send(message)).doesNotThrowAnyException();
    }
}
