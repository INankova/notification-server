package com.example.notification_service.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class UpsertNotificationPreference {

    @NotNull
    private UUID userId;

    private boolean notificationEnabled;

    @NotNull
    private NotificationTypeRequest type;


    @NotNull
    @NotBlank
    private String contactInfo;
}
