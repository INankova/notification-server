package com.example.notification_service.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventReminderRequest {
    @NotNull
    private UUID userId;

    @NotBlank
    private String subject;

    @NotBlank
    private String body;

    @NotNull
    private LocalDateTime eventStart;

    private List<Integer> offsetsMinutes;
}
