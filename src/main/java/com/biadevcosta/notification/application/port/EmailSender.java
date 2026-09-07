package com.biadevcosta.notification.application.port;

import com.biadevcosta.notification.domain.EmailMessage;

/**
 * Delivers an e-mail. The adapter talks to a concrete provider and translates any transport failure
 * into {@code EmailDeliveryException}, so the core never sees provider or HTTP types.
 */
public interface EmailSender {

    void send(EmailMessage message);
}
