package com.biadevcosta.notification.domain.exception;

/** Raised when the identity service has no user for the given patient id. */
public class PatientContactNotFoundException extends NotificationException {

    public PatientContactNotFoundException(String patientId) {
        super("Patient contact not found: " + patientId);
    }
}
