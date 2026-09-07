package com.biadevcosta.notification.application.port;

import com.biadevcosta.notification.domain.PatientContact;

/**
 * Resolves a patient's contact details by id. The adapter calls the identity service over HTTP
 * (with a cache) and raises {@code PatientContactNotFoundException} when the id is unknown.
 */
public interface UserDirectory {

    PatientContact findPatient(String patientId);
}
