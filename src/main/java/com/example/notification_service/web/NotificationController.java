package com.example.notification_service.web;

import com.example.notification_service.model.Notification;
import com.example.notification_service.model.NotificationPreference;
import com.example.notification_service.service.NotificationService;
import com.example.notification_service.web.dto.EventReminderRequest;
import com.example.notification_service.web.dto.NotificationPreferenceResponse;
import com.example.notification_service.web.dto.NotificationRequest;
import com.example.notification_service.web.dto.NotificationResponse;
import com.example.notification_service.web.dto.NotificationScheduleRequest;
import com.example.notification_service.web.dto.UpsertNotificationPreference;
import com.example.notification_service.web.mapper.DtoMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

        NotificationPreference notificationPreference = notificationService.upsertPreference(upsertPreference);

        NotificationPreferenceResponse notificationPreferenceResponse = DtoMapper.fromNotificationPreference(notificationPreference);

        return ResponseEntity.status(HttpStatus.CREATED).body(notificationPreferenceResponse);
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> getUserNotificationPreference(@RequestParam(name = "userId") UUID userId) {

        return notificationService.findPreferenceByUserId(userId)
                .map(pref -> {
                    NotificationPreferenceResponse responseDto = DtoMapper.fromNotificationPreference(pref);
                    return ResponseEntity.status(HttpStatus.OK).body(responseDto);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> sendNotification(@RequestBody NotificationRequest notificationRequest) {

        Notification notification = notificationService.sendNotification(notificationRequest);

        NotificationResponse notificationResponse = DtoMapper.fromNotification(notification);

        return ResponseEntity.status(HttpStatus.CREATED).body(notificationResponse);
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(@RequestParam(name = "userId") UUID userId) {

        List<NotificationResponse> notificationResponses = notificationService.getNotifications(userId)
                .stream()
                .map(DtoMapper::fromNotification)
                .toList();

        return ResponseEntity.status(HttpStatus.OK).body(notificationResponses);
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> changeNotificationPreference(@RequestParam(name = "userId") UUID userId,
                                                                                       @RequestParam(name = "enabled") boolean enabled) {

        NotificationPreference notificationPreference = notificationService.changeNotificationPreferenceStatus(userId, enabled);

        NotificationPreferenceResponse responseDto = DtoMapper.fromNotificationPreference(notificationPreference);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseDto);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearPreviousNotifications(@RequestParam(name = "userId") UUID userId) {

        notificationService.clearNotifications(userId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/reminders/schedule")
    public ResponseEntity<NotificationResponse> schedule(@Valid @RequestBody NotificationScheduleRequest req) {
        Notification n = notificationService.scheduleNotification(
                new NotificationRequest(req.getUserId(), req.getSubject(), req.getBody()),
                req.getScheduledAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(DtoMapper.fromNotification(n));
    }

    @PostMapping("/reminders/event")
    public ResponseEntity<List<NotificationResponse>> scheduleEventReminders(@Valid @RequestBody EventReminderRequest req) {
        List<Notification> list = notificationService.scheduleEventReminders(req);
        List<NotificationResponse> dto = list.stream().map(DtoMapper::fromNotification).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(DtoMapper.fromNotification(notificationService.getById(id)));
    }

}
