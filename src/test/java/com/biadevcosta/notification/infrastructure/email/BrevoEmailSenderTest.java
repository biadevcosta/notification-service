package com.biadevcosta.notification.infrastructure.email;

import com.biadevcosta.notification.domain.EmailMessage;
import com.biadevcosta.notification.domain.exception.EmailDeliveryException;
import com.biadevcosta.notification.infrastructure.config.EmailProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BrevoEmailSenderTest {

    private static final String API_URL = "https://api.brevo.com/v3/smtp/email";

    private MockRestServiceServer server;
    private BrevoEmailSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new BrevoEmailSender(builder,
                new EmailProperties(API_URL, "test-key", "no-reply@hospital.local", "Hospital Appointments"));
    }

    private EmailMessage sampleEmail() {
        return new EmailMessage("john@example.com", "John Doe", "Sua consulta", "<p>Olá, John Doe.</p>");
    }

    @Test
    void send_postsToBrevo_withApiKeyHeader_andMappedPayload() {
        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-key"))
                .andExpect(jsonPath("$.sender.email").value("no-reply@hospital.local"))
                .andExpect(jsonPath("$.to[0].email").value("john@example.com"))
                .andExpect(jsonPath("$.to[0].name").value("John Doe"))
                .andExpect(jsonPath("$.subject").value("Sua consulta"))
                .andExpect(jsonPath("$.htmlContent").value("<p>Olá, John Doe.</p>"))
                .andRespond(withSuccess("{\"messageId\":\"abc-123\"}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        sender.send(sampleEmail());

        server.verify();
    }

    @Test
    void send_whenBrevoRejects_throwsEmailDeliveryException() {
        server.expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> sender.send(sampleEmail()))
                .isInstanceOf(EmailDeliveryException.class);
        server.verify();
    }
}
