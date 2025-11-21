package app.notification;

import com.example.notification_service.exception.DisableNotificationPreferenceException;
import com.example.notification_service.exception.NotificationPreferenceNotFoundException;
import com.example.notification_service.model.*;
import com.example.notification_service.repository.NotificationPreferenceRepository;
import com.example.notification_service.repository.NotificationRepository;
import com.example.notification_service.service.NotificationService;
import com.example.notification_service.web.dto.EventReminderRequest;
import com.example.notification_service.web.dto.NotificationRequest;
import com.example.notification_service.web.dto.UpsertNotificationPreference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceUTest {

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private JavaMailSenderImpl mailSender;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void upsertNotification_shouldUpdateExistingPreference() {
        UUID userId = UUID.randomUUID();

        NotificationPreference existing = NotificationPreference.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .enabled(false)
                .contactInfo("old@example.com")
                .type(NotificationType.EMAIL)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(preferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UpsertNotificationPreference req = new UpsertNotificationPreference();
        req.setUserId(userId);
        req.setNotificationEnabled(true);
        req.setContactInfo("new@example.com");
        req.setType(com.example.notification_service.web.dto.NotificationTypeRequest.EMAIL);

        NotificationPreference result = notificationService.upsertPreference(req);

        assertEquals(userId, result.getUserId());
        assertTrue(result.isEnabled());
        assertEquals("new@example.com", result.getContactInfo());
        assertNotNull(result.getUpdatedOn());
        verify(preferenceRepository).save(existing);
    }

    @Test
    void upsertNotification_shouldCreateNewPreference_whenNotExists() {
        UUID userId = UUID.randomUUID();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UpsertNotificationPreference req = new UpsertNotificationPreference();
        req.setUserId(userId);
        req.setNotificationEnabled(true);
        req.setContactInfo("new@example.com");
        req.setType(com.example.notification_service.web.dto.NotificationTypeRequest.EMAIL);

        NotificationPreference result = notificationService.upsertPreference(req);

        assertEquals(userId, result.getUserId());
        assertTrue(result.isEnabled());
        assertEquals("new@example.com", result.getContactInfo());
        assertNotNull(result.getCreatedOn());
        assertNotNull(result.getUpdatedOn());
        verify(preferenceRepository).save(any(NotificationPreference.class));
    }

    @Test
    void getPreferenceByUserId_shouldReturnPreference_whenExists() {
        UUID userId = UUID.randomUUID();
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));

        NotificationPreference result = notificationService.getPreferenceByUserId(userId);

        assertSame(pref, result);
    }

    @Test
    void getPreferenceByUserId_shouldThrow_whenNotFound() {
        UUID userId = UUID.randomUUID();
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(NotificationPreferenceNotFoundException.class,
                () -> notificationService.getPreferenceByUserId(userId));
    }

    @Test
    void sendNotification_shouldSendEmailAndMarkSucceeded_whenPreferenceEnabled() {
        UUID userId = UUID.randomUUID();
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .contactInfo("user@example.com")
                .enabled(true)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationRequest req =
                new NotificationRequest(userId, "Subject", "Body");

        Notification result = notificationService.sendNotification(req);

        assertEquals(NotificationStatus.SUCCEEDED, result.getStatus());
        assertEquals(NotificationType.EMAIL, result.getType());
        assertEquals(userId, result.getUserId());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendNotification_shouldMarkFailed_whenMailSenderThrows() {
        UUID userId = UUID.randomUUID();
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .contactInfo("user@example.com")
                .enabled(true)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));
        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationRequest req =
                new NotificationRequest(userId, "Subject", "Body");

        Notification result = notificationService.sendNotification(req);

        assertEquals(NotificationStatus.FAILED, result.getStatus());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendNotification_shouldThrow_whenPreferenceDisabled() {
        UUID userId = UUID.randomUUID();
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .contactInfo("user@example.com")
                .enabled(false)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));

        NotificationRequest req =
                new NotificationRequest(userId, "Subject", "Body");

        assertThrows(DisableNotificationPreferenceException.class,
                () -> notificationService.sendNotification(req));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void getNotifications_shouldDelegateToRepository() {
        UUID userId = UUID.randomUUID();
        Notification n1 = Notification.builder().id(UUID.randomUUID()).userId(userId).build();
        Notification n2 = Notification.builder().id(UUID.randomUUID()).userId(userId).build();

        when(notificationRepository.findAllByUserIdAndDeleted(userId))
                .thenReturn(List.of(n1, n2));

        List<Notification> result = notificationService.getNotifications(userId);

        assertEquals(2, result.size());
        assertEquals(userId, result.get(0).getUserId());
        verify(notificationRepository).findAllByUserIdAndDeleted(userId);
    }

    @Test
    void changeNotificationPreferenceStatus_shouldUpdateEnabledFlag() {
        UUID userId = UUID.randomUUID();
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .enabled(false)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));
        when(preferenceRepository.save(pref)).thenReturn(pref);

        NotificationPreference result =
                notificationService.changeNotificationPreferenceStatus(userId, true);

        assertTrue(result.isEnabled());
        verify(preferenceRepository).save(pref);
    }

    @Test
    void clearNotifications_shouldMarkAllAsDeleted() {
        UUID userId = UUID.randomUUID();
        Notification n1 = Notification.builder().id(UUID.randomUUID()).userId(userId).deleted(false).build();
        Notification n2 = Notification.builder().id(UUID.randomUUID()).userId(userId).deleted(false).build();

        when(notificationRepository.findAllByUserIdAndDeleted(userId))
                .thenReturn(List.of(n1, n2));

        notificationService.clearNotifications(userId);

        assertTrue(n1.isDeleted());
        assertTrue(n2.isDeleted());
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    void scheduleNotification_shouldCreatePendingNotification_whenPreferenceEnabled() {
        UUID userId = UUID.randomUUID();
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .enabled(true)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime at = LocalDateTime.now().plusHours(1);
        NotificationRequest req = new NotificationRequest(userId, "Subj", "Body");

        Notification result = notificationService.scheduleNotification(req, at);

        assertEquals(NotificationStatus.PENDING, result.getStatus());
        assertEquals(at, result.getScheduledAt());
        assertEquals(0, result.getAttempts());
    }

    @Test
    void scheduleNotification_shouldThrow_whenPreferenceDisabled() {
        UUID userId = UUID.randomUUID();
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .enabled(false)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));

        NotificationRequest req = new NotificationRequest(userId, "Subj", "Body");

        assertThrows(DisableNotificationPreferenceException.class,
                () -> notificationService.scheduleNotification(req, LocalDateTime.now().plusHours(1)));
    }

    @Test
    void scheduleEventReminders_shouldUseDefaultOffsets_whenNull() {
        UUID userId = UUID.randomUUID();
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .enabled(true)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime eventStart = LocalDateTime.now().plusDays(2);

        EventReminderRequest r = new EventReminderRequest();
        r.setUserId(userId);
        r.setSubject("Event");
        r.setBody("Don't forget");
        r.setEventStart(eventStart);
        r.setOffsetsMinutes(null);

        List<Notification> result = notificationService.scheduleEventReminders(r);

        assertEquals(2, result.size());
        assertEquals(NotificationStatus.PENDING, result.get(0).getStatus());
    }

    @Test
    void scheduleEventReminders_shouldSkipOffsetsThatResultInPast() {
        UUID userId = UUID.randomUUID();
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .enabled(true)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime eventStart = LocalDateTime.now().plusMinutes(10);

        EventReminderRequest r = new EventReminderRequest();
        r.setUserId(userId);
        r.setSubject("Event");
        r.setBody("Body");
        r.setEventStart(eventStart);
        r.setOffsetsMinutes(List.of(5, 20));

        List<Notification> result = notificationService.scheduleEventReminders(r);

        assertEquals(1, result.size());
    }

    @Test
    void sendReminder_shouldScheduleAndSendEmail_andSaveResult() {
        UUID userId = UUID.randomUUID();
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .enabled(true)
                .contactInfo("user@example.com")
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationRequest req = new NotificationRequest(userId, "Subj", "Body");

        Notification result = notificationService.sendReminder(req);

        assertEquals(NotificationStatus.SUCCEEDED, result.getStatus());
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(notificationRepository, atLeastOnce()).save(any(Notification.class));
    }

    @Test
    void processDueNotifications_shouldMarkSucceeded_whenSendOk() {
        UUID userId = UUID.randomUUID();

        Notification n = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(NotificationStatus.PENDING)
                .scheduledAt(LocalDateTime.now().minusMinutes(1))
                .attempts(0)
                .build();

        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .enabled(true)
                .contactInfo("user@example.com")
                .build();

        when(notificationRepository.findAllByStatusAndScheduledAtBefore(eq(NotificationStatus.PENDING), any()))
                .thenReturn(List.of(n));
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));

        notificationService.processDueNotifications();

        assertEquals(NotificationStatus.SUCCEEDED, n.getStatus());
        assertNull(n.getLastError());
        verify(notificationRepository).save(n);
    }

    @Test
    void processDueNotifications_shouldIncreaseAttempts_andRescheduleOrFailOnError() {
        UUID userId = UUID.randomUUID();

        Notification n1 = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(NotificationStatus.PENDING)
                .scheduledAt(LocalDateTime.now().minusMinutes(5))
                .attempts(0)
                .build();

        Notification n2 = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(NotificationStatus.PENDING)
                .scheduledAt(LocalDateTime.now().minusMinutes(5))
                .attempts(2)
                .build();

        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .enabled(true)
                .contactInfo("user@example.com")
                .build();

        when(notificationRepository.findAllByStatusAndScheduledAtBefore(eq(NotificationStatus.PENDING), any()))
                .thenReturn(List.of(n1, n2));
        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(pref));

        doThrow(new RuntimeException("SMTP error"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        notificationService.processDueNotifications();

        assertEquals(1, n1.getAttempts());
        assertEquals(NotificationStatus.PENDING, n1.getStatus());
        assertNotNull(n1.getScheduledAt());
        assertEquals("SMTP error", n1.getLastError());

        assertEquals(3, n2.getAttempts());
        assertEquals(NotificationStatus.FAILED, n2.getStatus());
        assertEquals("SMTP error", n2.getLastError());

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    void getById_shouldReturnNotification_whenExists() {
        UUID id = UUID.randomUUID();
        Notification n = Notification.builder().id(id).build();

        when(notificationRepository.findById(id)).thenReturn(Optional.of(n));

        Notification result = notificationService.getById(id);

        assertSame(n, result);
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> notificationService.getById(id));
    }
}
