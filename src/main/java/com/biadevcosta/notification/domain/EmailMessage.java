package com.biadevcosta.notification.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A provider-neutral e-mail: recipient, subject and HTML body. No provider vocabulary
 * ("sender", "htmlContent", "api-key", ...) — the mapping to a concrete provider's request shape
 * is done inside its {@code EmailSender} adapter.
 */
public record EmailMessage(String toEmail, String toName, String subject, String htmlBody) {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    public EmailMessage {
        if (isBlank(toEmail)) {
            throw new IllegalArgumentException("toEmail is required");
        }
        if (isBlank(toName)) {
            throw new IllegalArgumentException("toName is required");
        }
        if (isBlank(subject)) {
            throw new IllegalArgumentException("subject is required");
        }
        if (isBlank(htmlBody)) {
            throw new IllegalArgumentException("htmlBody is required");
        }
    }

    /** Builds the appointment-reminder e-mail for a patient (patient-facing copy, pt-BR). */
    public static EmailMessage appointmentReminder(PatientContact patient, LocalDateTime scheduledAt) {
        String when = scheduledAt.format(WHEN);
        String subject = "Lembrete: sua consulta em " + when;
        String htmlBody = """
                <p>Olá, %s.</p>
                <p>Este é um lembrete da sua consulta marcada para <strong>%s</strong>.</p>
                <p>Se precisar remarcar ou tiver qualquer dúvida, entre em contato com o hospital.</p>
                """.formatted(patient.name(), when);
        return new EmailMessage(patient.email(), patient.name(), subject, htmlBody);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
