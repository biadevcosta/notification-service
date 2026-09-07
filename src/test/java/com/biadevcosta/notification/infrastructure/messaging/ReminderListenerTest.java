package com.biadevcosta.notification.infrastructure.messaging;

import com.biadevcosta.notification.application.command.SendReminderCommand;
import com.biadevcosta.notification.application.usecase.SendAppointmentReminderUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReminderListenerTest {

    @Mock
    SendAppointmentReminderUseCase useCase;

    @Test
    void onReminder_mapsMessageOntoCommand_andRunsTheUseCase() {
        LocalDateTime when = LocalDateTime.of(2030, 12, 1, 10, 30);

        new ReminderListener(useCase)
                .onReminder(new AppointmentReminderMessage("apt-1", "pat-1", when));

        ArgumentCaptor<SendReminderCommand> command = ArgumentCaptor.forClass(SendReminderCommand.class);
        verify(useCase).execute(command.capture());
        assertThat(command.getValue().appointmentId()).isEqualTo("apt-1");
        assertThat(command.getValue().patientId()).isEqualTo("pat-1");
        assertThat(command.getValue().scheduledAt()).isEqualTo(when);
    }
}
