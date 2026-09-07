package com.biadevcosta.notification.infrastructure.email;

import com.biadevcosta.notification.application.port.EmailSender;
import com.biadevcosta.notification.domain.EmailMessage;
import com.biadevcosta.notification.domain.exception.EmailDeliveryException;
import com.biadevcosta.notification.infrastructure.config.EmailProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * {@link EmailSender} backed by the Brevo transactional e-mail API. This class is the only place
 * that knows Brevo's request shape and the {@code api-key} header; the rest of the app deals with
 * the neutral {@link EmailMessage}. Any transport failure becomes an {@link EmailDeliveryException}.
 */
@Component
public class BrevoEmailSender implements EmailSender {

    private final RestClient restClient;
    private final String apiUrl;
    private final Sender sender;

    public BrevoEmailSender(RestClient.Builder builder, EmailProperties properties) {
        this.restClient = builder
                .defaultHeader("api-key", properties.apiKey())
                .defaultHeader("accept", "application/json")
                .build();
        this.apiUrl = properties.apiUrl();
        this.sender = new Sender(properties.fromName(), properties.fromEmail());
    }

    @Override
    public void send(EmailMessage message) {
        BrevoRequest request = new BrevoRequest(
                sender,
                List.of(new Recipient(message.toEmail(), message.toName())),
                message.subject(),
                message.htmlBody());
        try {
            restClient.post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new EmailDeliveryException("Brevo rejected the e-mail: " + e.getMessage(), e);
        }
    }

    private record BrevoRequest(Sender sender, List<Recipient> to, String subject, String htmlContent) {
    }

    private record Sender(String name, String email) {
    }

    private record Recipient(String email, String name) {
    }
}
