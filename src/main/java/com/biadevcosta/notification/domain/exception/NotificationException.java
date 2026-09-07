package com.biadevcosta.notification.domain.exception;

/** Base type for failures raised while handling a reminder (contact lookup, e-mail delivery, ...). */
public class NotificationException extends RuntimeException {

    public NotificationException(String message) {
        super(message);
    }

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
