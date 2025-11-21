package com.example.notification_service.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
