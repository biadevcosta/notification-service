package com.biadevcosta.notification.domain.exception;

/**
 * Raised when the e-mail provider fails to accept a message. The provider adapter translates its
 * transport errors (HTTP status, timeouts, ...) into this, so the core never sees provider types.
 */
public class EmailDeliveryException extends NotificationException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
