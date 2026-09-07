package com.biadevcosta.notification.application.usecase;

import com.biadevcosta.notification.application.command.SendReminderCommand;
import com.biadevcosta.notification.application.port.EmailSender;
import com.biadevcosta.notification.application.port.ProcessedReminderStore;
import com.biadevcosta.notification.application.port.UserDirectory;
import com.biadevcosta.notification.domain.EmailMessage;
import com.biadevcosta.notification.domain.PatientContact;
import com.biadevcosta.notification.domain.exception.EmailDeliveryException;
import com.biadevcosta.notification.domain.exception.PatientContactNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendAppointmentReminderUseCaseTest {

    private static final Instant FIXED = Instant.parse("2026-01-01T12:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED, ZoneOffset.UTC);
    private static final LocalDateTime WHEN = LocalDateTime.of(2030, 12, 1, 10, 30);

    @Mock
    UserDirectory userDirectory;
    @Mock
    EmailSender emailSender;
    @Mock
    ProcessedReminderStore processedReminderStore;

    private SendAppointmentReminderUseCase useCase() {
        return new SendAppointmentReminderUseCase(userDirectory, emailSender, processedReminderStore,
                Clock.fixed(FIXED, ZoneOffset.UTC));
    }

    private SendReminderCommand command() {
        return new SendReminderCommand("apt-1", "pat-1", WHEN);
    }

    @Test
    void alreadyProcessed_doesNothing() {
        when(processedReminderStore.isProcessed("apt-1")).thenReturn(true);

        useCase().execute(command());

        verifyNoInteractions(userDirectory, emailSender);
        verify(processedReminderStore, never()).markProcessed(any(), any());
    }

    @Test
    void happyPath_resolvesContact_sendsEmail_thenMarksProcessed() {
        when(processedReminderStore.isProcessed("apt-1")).thenReturn(false);
        when(userDirectory.findPatient("pat-1"))
                .thenReturn(new PatientContact("pat-1", "John Doe", "john@example.com"));

        useCase().execute(command());

        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().toEmail()).isEqualTo("john@example.com");
        assertThat(email.getValue().subject()).contains("01/12/2030 às 10:30");

        InOrder order = inOrder(processedReminderStore, userDirectory, emailSender);
        order.verify(processedReminderStore).isProcessed("apt-1");
        order.verify(userDirectory).findPatient("pat-1");
        order.verify(emailSender).send(any(EmailMessage.class));
        order.verify(processedReminderStore).markProcessed("apt-1", NOW);
    }

    @Test
    void contactLookupFails_doesNotSendOrMark() {
        when(processedReminderStore.isProcessed("apt-1")).thenReturn(false);
        when(userDirectory.findPatient("pat-1")).thenThrow(new PatientContactNotFoundException("pat-1"));

        assertThatThrownBy(() -> useCase().execute(command()))
                .isInstanceOf(PatientContactNotFoundException.class);

        verifyNoInteractions(emailSender);
        verify(processedReminderStore, never()).markProcessed(any(), any());
    }

    @Test
    void emailSendFails_doesNotMarkProcessed() {
        when(processedReminderStore.isProcessed("apt-1")).thenReturn(false);
        when(userDirectory.findPatient("pat-1"))
                .thenReturn(new PatientContact("pat-1", "John Doe", "john@example.com"));
        doThrow(new EmailDeliveryException("provider said 500"))
                .when(emailSender).send(any(EmailMessage.class));

        assertThatThrownBy(() -> useCase().execute(command()))
                .isInstanceOf(EmailDeliveryException.class);

        verify(processedReminderStore, never()).markProcessed(eq("apt-1"), any());
    }
}
