package com.biadevcosta.notification;

import com.biadevcosta.notification.infrastructure.messaging.AppointmentReminderMessage;
import com.biadevcosta.notification.support.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end: real MySQL + RabbitMQ (Testcontainers), identity-service and Brevo stubbed with
 * WireMock. Skipped when Docker is unavailable (see {@link AbstractIntegrationTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class NotificationIntegrationTest extends AbstractIntegrationTest {

    private static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void externalServices(DynamicPropertyRegistry registry) {
        registry.add("app.identity.base-url", WIREMOCK::baseUrl);
        registry.add("app.email.api-url", () -> WIREMOCK.baseUrl() + "/v3/smtp/email");
    }

    @Autowired
    RabbitTemplate rabbitTemplate;
    @Value("${app.reminder.rabbit.exchange}")
    String exchange;
    @Value("${app.reminder.rabbit.routing-key}")
    String routingKey;
    @Value("${app.reminder.rabbit.dlq}")
    String dlq;

    @BeforeEach
    void resetStubs() {
        WIREMOCK.resetAll();
    }

    private void publishReminder(String appointmentId, String patientId) {
        rabbitTemplate.convertAndSend(exchange, routingKey, new AppointmentReminderMessage(
                appointmentId, patientId, LocalDateTime.of(2030, 12, 1, 10, 30)));
    }

    private void awaitAtLeastBrevoCalls(int count) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            long calls = WIREMOCK.countRequestsMatching(
                    postRequestedFor(urlEqualTo("/v3/smtp/email")).build()).getCount();
            if (calls >= count) {
                return;
            }
            Thread.sleep(100);
        }
    }

    @Test
    void reminder_resolvesPatient_thenSendsEmailViaBrevo() throws Exception {
        WIREMOCK.stubFor(get(urlEqualTo("/users/pat-1")).willReturn(okJson(
                "{\"id\":\"pat-1\",\"name\":\"John Doe\",\"email\":\"john@example.com\",\"role\":\"PATIENT\"}")));
        WIREMOCK.stubFor(post(urlEqualTo("/v3/smtp/email"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"messageId\":\"m-1\"}")));

        publishReminder("apt-1", "pat-1");
        awaitAtLeastBrevoCalls(1);

        WIREMOCK.verify(postRequestedFor(urlEqualTo("/v3/smtp/email"))
                .withRequestBody(matchingJsonPath("$.to[0].email", equalTo("john@example.com"))));
    }

    @Test
    void duplicateReminder_sendsOnlyOneEmail() throws Exception {
        WIREMOCK.stubFor(get(urlEqualTo("/users/pat-2")).willReturn(okJson(
                "{\"id\":\"pat-2\",\"name\":\"Jane Roe\",\"email\":\"jane@example.com\",\"role\":\"PATIENT\"}")));
        WIREMOCK.stubFor(post(urlEqualTo("/v3/smtp/email")).willReturn(aResponse().withStatus(201)));

        publishReminder("apt-2", "pat-2");
        awaitAtLeastBrevoCalls(1);
        publishReminder("apt-2", "pat-2");
        Thread.sleep(1_000);

        WIREMOCK.verify(1, postRequestedFor(urlEqualTo("/v3/smtp/email")));
    }

    @Test
    void whenBrevoRejects_theMessageIsDeadLettered() {
        WIREMOCK.stubFor(get(urlEqualTo("/users/pat-3")).willReturn(okJson(
                "{\"id\":\"pat-3\",\"name\":\"Bob Poe\",\"email\":\"bob@example.com\",\"role\":\"PATIENT\"}")));
        WIREMOCK.stubFor(post(urlEqualTo("/v3/smtp/email")).willReturn(aResponse().withStatus(500)));

        publishReminder("apt-3", "pat-3");

        Message deadLettered = rabbitTemplate.receive(dlq, 10_000);
        assertThat(deadLettered).isNotNull();
    }
}
