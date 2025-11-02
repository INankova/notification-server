package com.example.notification_service.Exception;

public class NotificationPreferenceNotFoundException extends RuntimeException {
    public NotificationPreferenceNotFoundException(String message) {
        super(message);
    }

    public NotificationPreferenceNotFoundException() {}
}
