package com.biadevcosta.notification.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientContactTest {

    @Test
    void holdsTheGivenValues() {
        PatientContact contact = new PatientContact("pat-1", "John Doe", "john@example.com");

        assertThat(contact.id()).isEqualTo("pat-1");
        assertThat(contact.name()).isEqualTo("John Doe");
        assertThat(contact.email()).isEqualTo("john@example.com");
    }

    @Test
    void rejectsBlankFields() {
        assertThatThrownBy(() -> new PatientContact(" ", "John", "john@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatientContact("pat-1", null, "john@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatientContact("pat-1", "John", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
