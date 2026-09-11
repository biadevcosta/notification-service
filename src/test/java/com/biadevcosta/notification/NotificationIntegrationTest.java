package com.biadevcosta.notification;

import com.biadevcosta.notification.application.port.ProcessedReminderStore;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end: real MySQL + RabbitMQ (Testcontainers), identity-service stubbed with WireMock.
 * E-mail delivery is simulated by {@code LoggingEmailSender} (Brevo integration is a follow-up),
 * so "sent" is observed as the reminder being recorded in {@code processed_reminders}. Skipped
 * when Docker is unavailable (see {@link AbstractIntegrationTest}).
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
    }

    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    ProcessedReminderStore processedReminderStore;
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

    private void awaitProcessed(String appointmentId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (processedReminderStore.isProcessed(appointmentId)) {
                return;
            }
            Thread.sleep(100);
        }
    }

    @Test
    void reminder_resolvesPatient_thenRecordsAsProcessed() throws Exception {
        WIREMOCK.stubFor(get(urlEqualTo("/users/pat-1")).willReturn(okJson(
                "{\"id\":\"pat-1\",\"name\":\"John Doe\",\"email\":\"john@example.com\",\"role\":\"PATIENT\"}")));

        publishReminder("apt-1", "pat-1");
        awaitProcessed("apt-1");

        assertThat(processedReminderStore.isProcessed("apt-1")).isTrue();
    }

    @Test
    void duplicateReminder_resolvesThePatientOnlyOnce() throws Exception {
        WIREMOCK.stubFor(get(urlEqualTo("/users/pat-2")).willReturn(okJson(
                "{\"id\":\"pat-2\",\"name\":\"Jane Roe\",\"email\":\"jane@example.com\",\"role\":\"PATIENT\"}")));

        publishReminder("apt-2", "pat-2");
        awaitProcessed("apt-2");
        publishReminder("apt-2", "pat-2");
        Thread.sleep(1_000);

        WIREMOCK.verify(1, getRequestedFor(urlEqualTo("/users/pat-2")));
    }

    @Test
    void whenPatientLookupFails_theMessageIsDeadLettered() {
        WIREMOCK.stubFor(get(urlEqualTo("/users/pat-3")).willReturn(
                aResponse().withStatus(HttpStatus.NOT_FOUND.value())));

        publishReminder("apt-3", "pat-3");

        Message deadLettered = rabbitTemplate.receive(dlq, 10_000);
        assertThat(deadLettered).isNotNull();
    }
}
