package com.example.notification_service.web.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ResponseError {

    private int status;
    private String message;
    private LocalDateTime time;

    public ResponseError(int status, String message) {
        this.status = status;
        this.message = message;
        this.time = LocalDateTime.now();
    }
}
