package com.biadevcosta.notification.application.usecase;

import com.biadevcosta.notification.application.command.SendReminderCommand;
import com.biadevcosta.notification.application.port.EmailSender;
import com.biadevcosta.notification.application.port.ProcessedReminderStore;
import com.biadevcosta.notification.application.port.UserDirectory;
import com.biadevcosta.notification.domain.EmailMessage;
import com.biadevcosta.notification.domain.PatientContact;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Handles one reminder: skip if already processed (idempotent), otherwise resolve the patient's
 * contact, send the reminder e-mail, and record it as processed. Any failure propagates so the
 * inbound transport can retry / dead-letter; the reminder is marked processed only <em>after</em>
 * the e-mail has been accepted.
 */
public class SendAppointmentReminderUseCase {

    private final UserDirectory userDirectory;
    private final EmailSender emailSender;
    private final ProcessedReminderStore processedReminderStore;
    private final Clock clock;

    public SendAppointmentReminderUseCase(UserDirectory userDirectory,
                                         EmailSender emailSender,
                                         ProcessedReminderStore processedReminderStore,
                                         Clock clock) {
        this.userDirectory = userDirectory;
        this.emailSender = emailSender;
        this.processedReminderStore = processedReminderStore;
        this.clock = clock;
    }

    public void execute(SendReminderCommand command) {
        if (processedReminderStore.isProcessed(command.appointmentId())) {
            return;
        }
        PatientContact patient = userDirectory.findPatient(command.patientId());
        emailSender.send(EmailMessage.appointmentReminder(patient, command.scheduledAt()));
        processedReminderStore.markProcessed(command.appointmentId(), LocalDateTime.now(clock));
    }
}
