package com.example.notification_service.web.mapper;

import com.example.notification_service.model.Notification;
import com.example.notification_service.model.NotificationPreference;
import com.example.notification_service.model.NotificationType;
import com.example.notification_service.web.dto.NotificationPreferenceResponse;
import com.example.notification_service.web.dto.NotificationResponse;
import com.example.notification_service.web.dto.NotificationTypeRequest;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DtoMapper {

    public static NotificationType fromNotificationTypeRequest(NotificationTypeRequest notificationTypeRequest) {
        return switch (notificationTypeRequest) {
            case EMAIL -> NotificationType.EMAIL;
        };
    }

    public static NotificationPreferenceResponse fromNotificationPreference(NotificationPreference notificationPreference) {

        return NotificationPreferenceResponse.builder()
                .id(notificationPreference.getId())
                .type(notificationPreference.getType())
                .enabled(notificationPreference.isEnabled())
                .contactInfo(notificationPreference.getContactInfo())
                .userId(notificationPreference.getUserId())
                .build();
    }

    public static NotificationResponse fromNotification(Notification notification) {
        return NotificationResponse.builder()
                .subject(notification.getSubject())
                .created(notification.getCreated())
                .status(notification.getStatus())
                .type(notification.getType())
                .build();
    }
}
