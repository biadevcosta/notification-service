package com.biadevcosta.notification.domain;

/**
 * The patient's contact details, resolved from the identity service. A pure value object — the
 * transport used to fetch it (HTTP, cache, ...) lives behind the {@code UserDirectory} port.
 */
public record PatientContact(String id, String name, String email) {

    public PatientContact {
        if (isBlank(id)) {
            throw new IllegalArgumentException("patient id is required");
        }
        if (isBlank(name)) {
            throw new IllegalArgumentException("patient name is required");
        }
        if (isBlank(email)) {
            throw new IllegalArgumentException("patient email is required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
