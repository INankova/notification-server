package com.example.notification_service.web.dto;

import com.example.notification_service.model.NotificationStatus;
import com.example.notification_service.model.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {

    private String subject;

    private String body;

    private LocalDateTime created;

    private NotificationStatus status;

    private NotificationType type;


}
