package com.example.notification_service.service;

import com.example.notification_service.exception.DisableNotificationPreferenceException;
import com.example.notification_service.exception.NotificationPreferenceNotFoundException;
import com.example.notification_service.model.Notification;
import com.example.notification_service.model.NotificationPreference;
import com.example.notification_service.model.NotificationStatus;
import com.example.notification_service.model.NotificationType;
import com.example.notification_service.repository.NotificationPreferenceRepository;
import com.example.notification_service.repository.NotificationRepository;
import com.example.notification_service.web.dto.EventReminderRequest;
import com.example.notification_service.web.dto.NotificationRequest;
import com.example.notification_service.web.dto.UpsertNotificationPreference;
import com.example.notification_service.web.mapper.DtoMapper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class NotificationService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final JavaMailSender mailSender;
    private final NotificationRepository notificationRepository;

    @Autowired
    public NotificationService(NotificationPreferenceRepository preferenceRepository,
                               JavaMailSender mailSender,
                               NotificationRepository notificationRepository) {
        this.preferenceRepository = preferenceRepository;
        this.mailSender = mailSender;
        this.notificationRepository = notificationRepository;
    }

    public NotificationPreference upsertPreference(UpsertNotificationPreference dto) {

        Optional<NotificationPreference> userNotificationPreferenceOptional = preferenceRepository.findByUserId(dto.getUserId());

        if (userNotificationPreferenceOptional.isPresent()) {
            NotificationPreference preference = userNotificationPreferenceOptional.get();
            preference.setContactInfo(dto.getContactInfo());
            preference.setEnabled(dto.isNotificationEnabled());
            preference.setType(DtoMapper.fromNotificationTypeRequest(dto.getType()));
            preference.setUpdatedOn(LocalDateTime.now());
            return preferenceRepository.save(preference);
        }

        NotificationPreference notificationPreference = NotificationPreference.builder()
                .userId(dto.getUserId())
                .type(DtoMapper.fromNotificationTypeRequest(dto.getType()))
                .enabled(dto.isNotificationEnabled())
                .contactInfo(dto.getContactInfo())
                .createdOn(LocalDateTime.now())
                .updatedOn(LocalDateTime.now())
                .build();

        return preferenceRepository.save(notificationPreference);
    }

    public NotificationPreference getPreferenceByUserId(UUID userId) {
        return preferenceRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new NotificationPreferenceNotFoundException("Notification preference not found!"));
    }

    public Optional<NotificationPreference> findPreferenceByUserId(UUID userId) {
        return preferenceRepository.findByUserId(userId);
    }

    public Notification sendNotification(NotificationRequest notificationRequest) {

        UUID userId = notificationRequest.getUserId();
        NotificationPreference preferenceByUserId = getPreferenceByUserId(userId);

        if (!preferenceByUserId.isEnabled()) {
            throw new DisableNotificationPreferenceException("Notification preference is disabled!");
        }

        if (preferenceByUserId.getContactInfo() == null ||
                preferenceByUserId.getContactInfo().isBlank()) {
            throw new IllegalStateException("Contact email is empty for user " + userId);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(preferenceByUserId.getContactInfo());
        message.setSubject(notificationRequest.getSubject());
        message.setText(notificationRequest.getBody());

        Notification notification = Notification.builder()
                .subject(notificationRequest.getSubject())
                .body(notificationRequest.getBody())
                .userId(userId)
                .created(LocalDateTime.now())
                .deleted(false)
                .type(NotificationType.EMAIL)
                .build();

        try {
            mailSender.send(message);
            notification.setStatus(NotificationStatus.SUCCEEDED);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", preferenceByUserId.getContactInfo(), e.getMessage(), e);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setLastError(e.getMessage());
        }

        return notificationRepository.save(notification);
    }

    public List<Notification> getNotifications(UUID userId) {
        return notificationRepository.findAllByUserIdAndDeleted(userId);
    }

    public NotificationPreference changeNotificationPreferenceStatus(UUID userId, boolean enabled) {

        Optional<NotificationPreference> optionalPreference = preferenceRepository.findByUserId(userId);

        NotificationPreference pref;

        if (optionalPreference.isPresent()) {
            pref = optionalPreference.get();
            pref.setEnabled(enabled);
            pref.setUpdatedOn(LocalDateTime.now());
        } else {
            pref = NotificationPreference.builder()
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .enabled(false)
                    .contactInfo("")
                    .createdOn(LocalDateTime.now())
                    .updatedOn(LocalDateTime.now())
                    .build();
        }

        return preferenceRepository.save(pref);
    }


    public void clearNotifications(UUID userId) {
        List<Notification> notifications = getNotifications(userId);
        notifications.forEach(notification -> {
            notification.setDeleted(true);
            notificationRepository.save(notification);
        });
    }

    public Notification scheduleNotification(NotificationRequest req, LocalDateTime scheduledAt) {
        log.error("### scheduleNotification: userId={}, subject='{}', scheduledAt={}",
                req.getUserId(), req.getSubject(), scheduledAt);

        NotificationPreference pref = getPreferenceByUserId(req.getUserId());
        if (!pref.isEnabled()) {
            throw new DisableNotificationPreferenceException("Notification preference is disabled!");
        }

        Notification n = Notification.builder()
                .subject(req.getSubject())
                .body(req.getBody())
                .userId(req.getUserId())
                .created(LocalDateTime.now())
                .deleted(false)
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.PENDING)
                .scheduledAt(scheduledAt)
                .attempts(0)
                .build();

        Notification saved = notificationRepository.save(n);
        log.error("### scheduleNotification SAVED: id={}, status={}, scheduledAt={}",
                saved.getId(), saved.getStatus(), saved.getScheduledAt());

        return saved;
    }


    public List<Notification> scheduleEventReminders(EventReminderRequest r) {
        List<Integer> offsets = (r.getOffsetsMinutes() == null || r.getOffsetsMinutes().isEmpty())
                ? List.of(1440, 120)
                : r.getOffsetsMinutes();

        NotificationRequest base = new NotificationRequest(r.getUserId(), r.getSubject(), r.getBody());

        List<Notification> all = new ArrayList<>();
        for (Integer off : offsets) {
            LocalDateTime at = r.getEventStart().minusMinutes(off);
            if (at.isAfter(LocalDateTime.now())) {
                all.add(scheduleNotification(base, at));
            }
        }
        return all;
    }

    public Notification sendReminder(NotificationRequest req) {
        Notification n = scheduleNotification(req, null);
        try {
            sendEmail(n);
            n.setStatus(NotificationStatus.SUCCEEDED);
        } catch (Exception e) {
            n.setStatus(NotificationStatus.FAILED);
            n.setLastError(e.getMessage());
        }
        return notificationRepository.save(n);
    }

    private void sendEmail(Notification n) {
        NotificationPreference pref = getPreferenceByUserId(n.getUserId());

        if (pref.getContactInfo() == null || pref.getContactInfo().isBlank()) {
            throw new IllegalStateException("Contact email is empty for user " + n.getUserId());
        }

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(pref.getContactInfo());
        msg.setSubject(n.getSubject());
        msg.setText(n.getBody());
        mailSender.send(msg);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processDueNotifications() {
        LocalDateTime now = LocalDateTime.now();
        List<Notification> due = notificationRepository
                .findAllByStatusAndScheduledAtBefore(NotificationStatus.PENDING, now);

        log.error("### processDueNotifications: found {} pending notifications", due.size());

        for (Notification n : due) {
            try {
                log.error("### processDueNotifications: sending id={}, userId={}, subject='{}'",
                        n.getId(), n.getUserId(), n.getSubject());

                sendEmail(n);
                n.setStatus(NotificationStatus.SUCCEEDED);
                n.setLastError(null);
            } catch (Exception e) {
                int attempts = (n.getAttempts() == null ? 0 : n.getAttempts()) + 1;
                n.setAttempts(attempts);
                n.setLastError(e.getMessage());
                if (attempts >= 3) {
                    n.setStatus(NotificationStatus.FAILED);
                } else {
                    n.setScheduledAt(now.plusMinutes(2));
                }
            }
            notificationRepository.save(n);
        }
    }


    public Notification getById(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
    }
}
