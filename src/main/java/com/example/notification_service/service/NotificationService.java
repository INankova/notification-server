package com.example.notification_service.service;

import com.example.notification_service.model.Notification;
import com.example.notification_service.model.NotificationPreference;
import com.example.notification_service.model.NotificationStatus;
import com.example.notification_service.model.NotificationType;
import com.example.notification_service.repository.NotificationPreferenceRepository;
import com.example.notification_service.repository.NotificationRepository;
import com.example.notification_service.web.dto.NotificationPreferenceResponse;
import com.example.notification_service.web.dto.NotificationRequest;
import com.example.notification_service.web.dto.UpsertNotificationPreference;
import com.example.notification_service.web.mapper.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final JavaMailSenderImpl mailSender;
    private final NotificationRepository notificationRepository;

    @Autowired
    public NotificationService(NotificationPreferenceRepository preferenceRepository, JavaMailSenderImpl mailSender, NotificationRepository notificationRepository) {
        this.preferenceRepository = preferenceRepository;
        this.mailSender = mailSender;

        this.notificationRepository = notificationRepository;
    }

    public NotificationPreference upsertNotification(UpsertNotificationPreference upsertPreference) {

        Optional<NotificationPreference> optionalNotificationPreference = preferenceRepository.findByUserId(upsertPreference.getUserId());

        if (optionalNotificationPreference.isPresent()) {
            NotificationPreference preference = optionalNotificationPreference.get();
            preference.setType(DtoMapper.fromNotificationTypeRequest(upsertPreference.getType()));
            preference.setEnabled(upsertPreference.isNotificationEnabled());
            preference.setContactInfo(upsertPreference.getContactInfo());
            preference.setUpdatedOn(LocalDateTime.now());

            return preferenceRepository.save(preference);
        }

        NotificationPreference notificationPreference = NotificationPreference.builder()
                .userId(upsertPreference.getUserId())
                .type(DtoMapper.fromNotificationTypeRequest(upsertPreference.getType()))
                .enabled(upsertPreference.isNotificationEnabled())
                .contactInfo(upsertPreference.getContactInfo())
                .createdOn(LocalDateTime.now())
                .updatedOn(LocalDateTime.now())
                .build();

        return preferenceRepository.save(notificationPreference);
    }

    public NotificationPreference getPreferenceByUserId(UUID userId) {

        return preferenceRepository.findByUserId(userId).orElseThrow(() -> new NullPointerException("Notification reference not found!"));
    }

    public Notification sendNotification(NotificationRequest notificationRequest) {

        UUID userId = notificationRequest.getUserId();
        NotificationPreference preferenceByUserId = getPreferenceByUserId(userId);

        if (!preferenceByUserId.isEnabled()) {
            throw new IllegalArgumentException("Notification reference is disabled!");
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
            notification.setStatus(NotificationStatus.FAILED);
        }

        return notificationRepository.save(notification);

    }

    public List<Notification> getNotifications(UUID userId) {

        List<Notification> notifications = notificationRepository.findAllByUserIdAndDeleted(userId);

        return notifications;
    }
}
