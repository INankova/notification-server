package com.example.notification_service.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class NotificationScheduleRequest {

    @NotNull
    private UUID userId;

    @NotBlank
    private String subject;

    @NotBlank
    private String body;

    @NotNull
    private LocalDateTime scheduledAt;
}
