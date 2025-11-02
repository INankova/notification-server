package com.example.notification_service.service;

import com.example.notification_service.Exception.DisableNotificationPreferenceException;
import com.example.notification_service.Exception.NotificationPreferenceNotFoundException;
import com.example.notification_service.client.EventClient;
import com.example.notification_service.model.DigestSendLog;
import com.example.notification_service.model.DigestStatus;
import com.example.notification_service.model.Notification;
import com.example.notification_service.model.NotificationPreference;
import com.example.notification_service.model.NotificationStatus;
import com.example.notification_service.model.NotificationType;
import com.example.notification_service.repository.DigestSendLogRepository;
import com.example.notification_service.repository.NotificationPreferenceRepository;
import com.example.notification_service.repository.NotificationRepository;
import com.example.notification_service.web.dto.EventSummary;
import com.example.notification_service.web.dto.NotificationRequest;
import com.example.notification_service.web.dto.UpsertNotificationPreference;
import com.example.notification_service.web.mapper.DtoMapper;
import jakarta.transaction.Transactional;
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
    private final DigestSendLogRepository digestSendLogRepository;
    private final EventClient eventClient;

    @Autowired
    public NotificationService(NotificationPreferenceRepository preferenceRepository, JavaMailSenderImpl mailSender, NotificationRepository notificationRepository, DigestSendLogRepository digestSendLogRepository, EventClient eventClient) {
        this.preferenceRepository = preferenceRepository;
        this.mailSender = mailSender;

        this.notificationRepository = notificationRepository;
        this.digestSendLogRepository = digestSendLogRepository;
        this.eventClient = eventClient;
    }

    public NotificationPreference upsertNotification(UpsertNotificationPreference upsertPreference) {

        Optional<NotificationPreference> optionalPreference = preferenceRepository.findByUserId(upsertPreference.getUserId());

        if (optionalPreference.isPresent()) {
            NotificationPreference preference = optionalPreference.get();
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

        return preferenceRepository.findByUserId(userId).orElseThrow(() -> new NotificationPreferenceNotFoundException("Notification reference not found!"));
    }

    public Notification sendNotification(NotificationRequest notificationRequest) {

        UUID userId = notificationRequest.getUserId();
        NotificationPreference preferenceByUserId = getPreferenceByUserId(userId);

        if (!preferenceByUserId.isEnabled()) {
            throw new DisableNotificationPreferenceException("Notification reference is disabled!");
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

        return notificationRepository.findAllByUserIdAndDeleted(userId);
    }

    public NotificationPreference changeNotificationPreferenceStatus(UUID userId, boolean enabled) {

        NotificationPreference notificationPreference = getPreferenceByUserId(userId);
        notificationPreference.setEnabled(enabled);
        return preferenceRepository.save(notificationPreference);
    }

    public void clearNotifications(UUID userId) {

        List<Notification> notifications = getNotifications(userId);

        notifications.forEach(notification -> {
            notification.setDeleted(true);
            notificationRepository.save(notification);
        });
    }

    @Transactional
    public void sendWeeklyDigest(LocalDateTime periodStart, LocalDateTime periodEnd) {
        var subscribers = preferenceRepository.findAllByEnabledTrueAndType(NotificationType.EMAIL);
        String body = buildDigestBody(periodStart, periodEnd);

        for (var pref : subscribers) {
            // 1) анти-дубликат проверка
            boolean alreadySent = digestSendLogRepository
                    .existsByUserIdAndPeriodStartAndPeriodEnd(pref.getUserId(), periodStart, periodEnd);
            if (alreadySent) {
                continue;
            }

            var msg = new SimpleMailMessage();
            msg.setTo(pref.getContactInfo());
            msg.setSubject("Седмичен дайджест: нови събития");
            msg.setText(body);

            var n = Notification.builder()
                    .userId(pref.getUserId())
                    .subject(msg.getSubject())
                    .body(msg.getText())
                    .type(NotificationType.EMAIL)
                    .created(LocalDateTime.now())
                    .deleted(false)
                    .build();

            DigestStatus status;
            String error = null;

            try {
                mailSender.send(msg);
                n.setStatus(NotificationStatus.SUCCEEDED);
                status = DigestStatus.SENT;
            } catch (Exception e) {
                n.setStatus(NotificationStatus.FAILED);
                status = DigestStatus.FAILED;
                error = e.getMessage();
            }

            notificationRepository.save(n);

            // 2) запиши лога (дори при FAILED, за да имаш следа; по желание може да логваш само при SENT)
            var log = DigestSendLog.builder()
                    .userId(pref.getUserId())
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .sentAt(LocalDateTime.now())
                    .status(status)
                    .errorMessage(error)
                    .build();

            try {
                digestSendLogRepository.save(log);
            } catch (Exception ignoreUnique) {
                // Ако две нишки опитат едновременно, уникалният констрейнт ще пази от дублиране
            }
        }
    }

    private String buildDigestBody(LocalDateTime from, LocalDateTime to) {
        var res = eventClient.listBetween(from.toString(), to.toString());

        List<EventSummary> events = (res.getStatusCode().is2xxSuccessful() && res.getBody() != null)
                ? res.getBody()
                : List.of();

        if (events.isEmpty()) {
            return """
                    Здравей!
                    
                    Нямаме нови събития за периода %s – %s.
                    Ще ти пишем пак следващата седмица. 👋
                    """.formatted(from.toLocalDate(), to.toLocalDate());
        }

        var sb = new StringBuilder();
        sb.append("Здравей!\n\n");
        sb.append("Ето новите събития за периода ").append(from.toLocalDate())
                .append(" – ").append(to.toLocalDate()).append(":\n\n");

        events.forEach(e -> sb.append("• ")
                .append(e.getTitle())
                .append(" — ").append(e.getDateTime())
                .append(", ").append(e.getLocation() != null ? e.getLocation() : "")
                .append(e.getPrice() != null ? " (цена: " + e.getPrice() + " лв.)" : "")
                .append("\n"));

        sb.append("\nПриятен уикенд! 👋");
        return sb.toString();
    }
}
