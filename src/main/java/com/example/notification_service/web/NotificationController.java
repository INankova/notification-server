package com.example.notification_service.web;

import com.example.notification_service.model.Notification;
import com.example.notification_service.model.NotificationPreference;
import com.example.notification_service.service.NotificationService;
import com.example.notification_service.web.dto.NotificationPreferenceResponse;
import com.example.notification_service.web.dto.NotificationRequest;
import com.example.notification_service.web.dto.NotificationResponse;
import com.example.notification_service.web.dto.UpsertNotificationPreference;
import com.example.notification_service.web.mapper.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Autowired
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> upsertNotificationPreference(@RequestBody UpsertNotificationPreference upsertPreference) {

       NotificationPreference notificationPreference = notificationService.upsertNotification(upsertPreference);

       NotificationPreferenceResponse notificationPreferenceResponse = DtoMapper.fromNotificationPreference(notificationPreference);

       return ResponseEntity.status(HttpStatus.CREATED).body(notificationPreferenceResponse);
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> getUserNotificationPreference(@RequestParam(name = "userId") UUID userId) {

     NotificationPreference notificationPreference = notificationService.getPreferenceByUserId(userId);

     NotificationPreferenceResponse responseDto = DtoMapper.fromNotificationPreference(notificationPreference);

     return ResponseEntity
            .status(HttpStatus.OK)
            .body(responseDto);
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> sendNotification(@RequestBody NotificationRequest notificationRequest) {

        Notification notification = notificationService.sendNotification(notificationRequest);

        NotificationResponse notificationResponse = DtoMapper.fromNotification(notification);

        return ResponseEntity.status(HttpStatus.CREATED).body(notificationResponse);
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(@RequestParam(name = "userId") UUID userId) {

        List<NotificationResponse> notificationResponses = notificationService.getNotifications(userId).stream().map(DtoMapper::fromNotification).toList();

        return ResponseEntity.status(HttpStatus.OK).body(notificationResponses);
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> changeNotificationPreference(@RequestParam(name = "userId") UUID userId, @RequestParam(name = "enabled") boolean enabled) {

        NotificationPreference notificationPreference = notificationService.changeNotificationPreferenceStatus(userId, enabled);

        NotificationPreferenceResponse responseDto = DtoMapper.fromNotificationPreference(notificationPreference);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseDto);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearPreviousNotifications(@RequestParam(name = "userId") UUID userId) {

        notificationService.clearNotifications(userId);

        return ResponseEntity.ok().body(null);
    }

    @PostMapping("/digest/weekly/run-now")
    public ResponseEntity<Void> runWeeklyDigestNow() {
        LocalDateTime end = LocalDateTime.now();
        notificationService.sendWeeklyDigest(end.minusDays(7), end);
        return ResponseEntity.accepted().build();
    }

}
