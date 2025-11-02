package com.example.notification_service.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jdk.jfr.EventType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class EventSummary {

    private UUID id;

    private String title;

    private LocalDateTime dateTime;

    private String location;
    @DecimalMin("0.0")
    private BigDecimal price;

    private EventType eventType;
}
