package com.biadevcosta.notification.infrastructure.email;

import com.biadevcosta.notification.application.port.EmailSender;
import com.biadevcosta.notification.domain.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link EmailSender} that simulates delivery by logging instead of calling a real provider.
 * Stands in for the Brevo integration (planned as a follow-up): swapping this out for a real
 * adapter later is a new {@code EmailSender} implementation, nothing else changes.
 */
@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("Simulated e-mail sent to {} <{}> — subject: \"{}\"",
                message.toName(), message.toEmail(), message.subject());
    }
}
