package com.biadevcosta.notification.infrastructure.client;

import com.biadevcosta.notification.domain.PatientContact;
import com.biadevcosta.notification.domain.exception.PatientContactNotFoundException;
import com.biadevcosta.notification.infrastructure.config.IdentityClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IdentityHttpUserDirectoryTest {

    private MockRestServiceServer server;
    private IdentityHttpUserDirectory directory;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        directory = new IdentityHttpUserDirectory(builder, new IdentityClientProperties("http://identity:8080"));
    }

    @Test
    void findPatient_mapsIdentityResponseToContact() {
        server.expect(requestTo("http://identity:8080/users/pat-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        { "id": "pat-1", "name": "John Doe", "email": "john@example.com", "role": "PATIENT" }
                        """, MediaType.APPLICATION_JSON));

        PatientContact contact = directory.findPatient("pat-1");

        assertThat(contact).isEqualTo(new PatientContact("pat-1", "John Doe", "john@example.com"));
        server.verify();
    }

    @Test
    void findPatient_whenIdentityReturns404_throwsPatientContactNotFound() {
        server.expect(requestTo("http://identity:8080/users/ghost"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> directory.findPatient("ghost"))
                .isInstanceOf(PatientContactNotFoundException.class);
        server.verify();
    }
}
