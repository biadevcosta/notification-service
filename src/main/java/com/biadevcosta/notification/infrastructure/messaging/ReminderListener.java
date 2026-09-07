package com.biadevcosta.notification.infrastructure.messaging;

import com.biadevcosta.notification.application.command.SendReminderCommand;
import com.biadevcosta.notification.application.usecase.SendAppointmentReminderUseCase;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: maps a {@code reminder.queue} message onto a {@link SendReminderCommand} and runs
 * the use case. Swapping RabbitMQ for another transport means a new adapter here, nothing else.
 * An exception propagates so the container retries and, once exhausted, dead-letters the message.
 */
@Component
public class ReminderListener {

    private final SendAppointmentReminderUseCase useCase;

    public ReminderListener(SendAppointmentReminderUseCase useCase) {
        this.useCase = useCase;
    }

    @RabbitListener(queues = "${app.reminder.rabbit.queue}")
    public void onReminder(AppointmentReminderMessage message) {
        useCase.execute(new SendReminderCommand(
                message.appointmentId(), message.patientId(), message.scheduledAt()));
    }
}
