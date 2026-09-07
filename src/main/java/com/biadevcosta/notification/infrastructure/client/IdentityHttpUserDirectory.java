package com.biadevcosta.notification.infrastructure.client;

import com.biadevcosta.notification.application.port.UserDirectory;
import com.biadevcosta.notification.domain.PatientContact;
import com.biadevcosta.notification.domain.exception.PatientContactNotFoundException;
import com.biadevcosta.notification.infrastructure.config.IdentityClientProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * {@link UserDirectory} backed by {@code identity-service} over HTTP. Results are cached
 * ({@code patient-contacts}) so a burst of reminders for the same patient hits identity once.
 */
@Component
public class IdentityHttpUserDirectory implements UserDirectory {

    private final RestClient restClient;

    public IdentityHttpUserDirectory(RestClient.Builder builder, IdentityClientProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    @Cacheable("patient-contacts")
    public PatientContact findPatient(String patientId) {
        try {
            IdentityUser user = restClient.get()
                    .uri("/users/{id}", patientId)
                    .retrieve()
                    .body(IdentityUser.class);
            return new PatientContact(user.id(), user.name(), user.email());
        } catch (HttpClientErrorException.NotFound e) {
            throw new PatientContactNotFoundException(patientId);
        }
    }

    /** Shape of identity-service's {@code GET /users/{id}} response (only what we need). */
    private record IdentityUser(String id, String name, String email, String role) {
    }
}
