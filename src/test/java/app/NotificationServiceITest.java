package app;

import com.example.notification_service.Application;
import com.example.notification_service.exception.DisableNotificationPreferenceException;
import com.example.notification_service.exception.NotificationPreferenceNotFoundException;
import com.example.notification_service.model.Notification;
import com.example.notification_service.model.NotificationPreference;
import com.example.notification_service.model.NotificationStatus;
import com.example.notification_service.model.NotificationType;
import com.example.notification_service.repository.NotificationPreferenceRepository;
import com.example.notification_service.repository.NotificationRepository;
import com.example.notification_service.service.NotificationService;
import com.example.notification_service.web.dto.EventReminderRequest;
import com.example.notification_service.web.dto.NotificationRequest;
import com.example.notification_service.web.dto.NotificationTypeRequest;
import com.example.notification_service.web.dto.UpsertNotificationPreference;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase
@Transactional
@Import(NotificationServiceITest.TestConfig.class)
class NotificationServiceITest {

    @TestConfiguration
    static class TestConfig {

        static class TestMailSender extends JavaMailSenderImpl {
            @Getter
            private final List<SimpleMailMessage> sentMessages = new ArrayList<>();
            @Setter
            private boolean failNext = false;

            @Override
            public void send(@NotNull SimpleMailMessage simpleMessage) {
                if (failNext) {
                    failNext = false;
                    throw new RuntimeException("SMTP down (simulated)");
                }
                sentMessages.add(simpleMessage);
            }

            public void clear() {
                sentMessages.clear();
                failNext = false;
            }

        }

        @Bean
        @Primary
        public JavaMailSenderImpl testMailSender() {
            return new TestMailSender();
        }
    }

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JavaMailSenderImpl mailSender;

    @BeforeEach
    void cleanDbAndState() {
        notificationRepository.deleteAll();
        preferenceRepository.deleteAll();
        if (mailSender instanceof TestConfig.TestMailSender testMailSender) {
            testMailSender.clear();
        }
    }

    private NotificationPreference createEnabledPreference(UUID userId, String email) {
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .type(NotificationType.EMAIL)
                .enabled(true)
                .contactInfo(email)
                .createdOn(LocalDateTime.now())
                .updatedOn(LocalDateTime.now())
                .build();
        return preferenceRepository.save(pref);
    }

    private void createDisabledPreference(UUID userId, String email) {
        NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .type(NotificationType.EMAIL)
                .enabled(false)
                .contactInfo(email)
                .createdOn(LocalDateTime.now())
                .updatedOn(LocalDateTime.now())
                .build();
        preferenceRepository.save(pref);
    }

    @Test
    void sendNotification_shouldSendEmailAndPersistSucceededNotification_whenPreferenceEnabled() {
        UUID userId = UUID.randomUUID();
        createEnabledPreference(userId, "test@example.com");

        NotificationRequest req =
                new NotificationRequest(userId, "Hello", "Test body");

        Notification result = notificationService.sendNotification(req);

        assertNotNull(result.getId());
        assertEquals(userId, result.getUserId());
        assertEquals("Hello", result.getSubject());
        assertEquals("Test body", result.getBody());
        assertEquals(NotificationType.EMAIL, result.getType());
        assertEquals(NotificationStatus.SUCCEEDED, result.getStatus());
        assertFalse(result.isDeleted());

        var testMailSender = (TestConfig.TestMailSender) mailSender;
        assertEquals(1, testMailSender.getSentMessages().size());
        SimpleMailMessage sent = testMailSender.getSentMessages().get(0);
        assertArrayEquals(new String[]{"test@example.com"}, sent.getTo());
        assertEquals("Hello", sent.getSubject());
        assertEquals("Test body", sent.getText());

        Notification fromDb = notificationRepository.findById(result.getId())
                .orElseThrow();
        assertEquals(NotificationStatus.SUCCEEDED, fromDb.getStatus());
    }

    @Test
    void sendNotification_shouldPersistFailedNotification_whenMailSenderThrows() {
        UUID userId = UUID.randomUUID();
        createEnabledPreference(userId, "fail@example.com");

        NotificationRequest req =
                new NotificationRequest(userId, "Subj", "Body");

        var testMailSender = (TestConfig.TestMailSender) mailSender;
        testMailSender.setFailNext(true);

        Notification result = notificationService.sendNotification(req);

        assertEquals(NotificationStatus.FAILED, result.getStatus());

        Notification fromDb = notificationRepository.findById(result.getId())
                .orElseThrow();
        assertEquals(NotificationStatus.FAILED, fromDb.getStatus());
    }

    @Test
    void sendNotification_shouldThrow_whenPreferenceDisabled() {
        UUID userId = UUID.randomUUID();
        createDisabledPreference(userId, "test@example.com");

        NotificationRequest req =
                new NotificationRequest(userId, "Subj", "Body");

        assertThrows(DisableNotificationPreferenceException.class,
                () -> notificationService.sendNotification(req));

        assertTrue(notificationRepository.findAll().isEmpty());
        var testMailSender = (TestConfig.TestMailSender) mailSender;
        assertTrue(testMailSender.getSentMessages().isEmpty());
    }

    @Test
    void getNotifications_shouldReturnOnlyNotDeleted() {
        UUID userId = UUID.randomUUID();
        createEnabledPreference(userId, "u@example.com");

        Notification n1 = Notification.builder()
                .userId(userId)
                .subject("A")
                .body("A body")
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.SUCCEEDED)
                .created(LocalDateTime.now())
                .deleted(false)
                .build();

        Notification n2 = Notification.builder()
                .userId(userId)
                .subject("B")
                .body("B body")
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.SUCCEEDED)
                .created(LocalDateTime.now())
                .deleted(true)
                .build();

        notificationRepository.save(n1);
        notificationRepository.save(n2);

        List<Notification> list = notificationService.getNotifications(userId);

        assertEquals(1, list.size());
        assertEquals("A", list.get(0).getSubject());
        assertFalse(list.get(0).isDeleted());
    }

    @Test
    void clearNotifications_shouldMarkAllAsDeleted_forGivenUser() {
        UUID userId = UUID.randomUUID();
        createEnabledPreference(userId, "u@example.com");

        Notification n1 = Notification.builder()
                .userId(userId)
                .subject("A")
                .body("A body")
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.SUCCEEDED)
                .created(LocalDateTime.now())
                .deleted(false)
                .build();

        Notification n2 = Notification.builder()
                .userId(userId)
                .subject("B")
                .body("B body")
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.SUCCEEDED)
                .created(LocalDateTime.now())
                .deleted(false)
                .build();

        notificationRepository.save(n1);
        notificationRepository.save(n2);

        notificationService.clearNotifications(userId);

        List<Notification> all = notificationRepository.findAll();
        assertEquals(2, all.size());
        assertTrue(all.get(0).isDeleted());
        assertTrue(all.get(1).isDeleted());
    }

    @Test
    void changeNotificationPreferenceStatus_shouldUpdateEnabledFlag() {
        UUID userId = UUID.randomUUID();
        NotificationPreference pref = createEnabledPreference(userId, "u@example.com");
        assertTrue(pref.isEnabled());

        NotificationPreference updated =
                notificationService.changeNotificationPreferenceStatus(userId, false);

        assertFalse(updated.isEnabled());

        NotificationPreference fromDb =
                preferenceRepository.findByUserId(userId).orElseThrow();
        assertFalse(fromDb.isEnabled());
    }

    @Test
    void scheduleNotification_shouldCreatePendingNotificationWithScheduledAt() {
        UUID userId = UUID.randomUUID();
        createEnabledPreference(userId, "u@example.com");

        NotificationRequest req =
                new NotificationRequest(userId, "Reminder", "Soon...");
        LocalDateTime scheduledAt = LocalDateTime.now().plusMinutes(30);

        Notification scheduled = notificationService.scheduleNotification(req, scheduledAt);

        assertNotNull(scheduled.getId());
        assertEquals(NotificationStatus.PENDING, scheduled.getStatus());
        assertEquals(scheduledAt, scheduled.getScheduledAt());
        assertEquals(0, scheduled.getAttempts());
        assertFalse(scheduled.isDeleted());
    }

    @Test
    void processDueNotifications_shouldSendPendingNotificationsAndMarkSucceeded() {
        UUID userId = UUID.randomUUID();
        createEnabledPreference(userId, "u@example.com");

        Notification pending = Notification.builder()
                .userId(userId)
                .subject("Due")
                .body("Time to send")
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.PENDING)
                .created(LocalDateTime.now().minusHours(1))
                .scheduledAt(LocalDateTime.now().minusMinutes(5))
                .deleted(false)
                .attempts(0)
                .build();

        pending = notificationRepository.save(pending);

        notificationService.processDueNotifications();

        Notification fromDb = notificationRepository.findById(pending.getId())
                .orElseThrow();

        assertEquals(NotificationStatus.SUCCEEDED, fromDb.getStatus());
        assertNull(fromDb.getLastError());
        assertEquals(0, fromDb.getAttempts());

        var testMailSender = (TestConfig.TestMailSender) mailSender;
        assertEquals(1, testMailSender.getSentMessages().size());
    }

    @Test
    void upsertPreference_shouldCreateNewPreference_whenNotExists() {
        UUID userId = UUID.randomUUID();

        UpsertNotificationPreference dto = UpsertNotificationPreference.builder()
                .userId(userId)
                .type(NotificationTypeRequest.EMAIL)
                .contactInfo("new@example.com")
                .notificationEnabled(true)
                .build();

        NotificationPreference pref = notificationService.upsertPreference(dto);

        assertNotNull(pref.getId());
        assertEquals(userId, pref.getUserId());
        assertEquals("new@example.com", pref.getContactInfo());
        assertTrue(pref.isEnabled());
        assertEquals(NotificationType.EMAIL, pref.getType());

        Optional<NotificationPreference> fromDb = preferenceRepository.findByUserId(userId);
        assertTrue(fromDb.isPresent());
        assertEquals("new@example.com", fromDb.get().getContactInfo());
    }

    @Test
    void upsertPreference_shouldUpdateExistingPreference_whenExists() {
        UUID userId = UUID.randomUUID();
        NotificationPreference existing =
                createEnabledPreference(userId, "old@example.com");

        existing.setUpdatedOn(existing.getUpdatedOn().minusMinutes(5));
        existing = preferenceRepository.save(existing);

        LocalDateTime oldUpdated = existing.getUpdatedOn();

        UpsertNotificationPreference dto = UpsertNotificationPreference.builder()
                .userId(userId)
                .type(NotificationTypeRequest.EMAIL)
                .contactInfo("updated@example.com")
                .notificationEnabled(false)
                .build();

        NotificationPreference updated = notificationService.upsertPreference(dto);

        assertEquals("updated@example.com", updated.getContactInfo());
        assertFalse(updated.isEnabled());
        assertEquals(NotificationType.EMAIL, updated.getType());
        assertTrue(updated.getUpdatedOn().isAfter(oldUpdated));
    }

    @Test
    void getPreferenceByUserId_shouldThrowWhenNotExists() {
        UUID userId = UUID.randomUUID();

        assertThrows(NotificationPreferenceNotFoundException.class,
                () -> notificationService.getPreferenceByUserId(userId));
    }

    @Test
    void findPreferenceByUserId_shouldReturnEmptyWhenNotExists() {
        UUID userId = UUID.randomUUID();

        Optional<NotificationPreference> result =
                notificationService.findPreferenceByUserId(userId);

        assertTrue(result.isEmpty());
    }

    @Test
    void sendNotification_shouldThrow_whenContactInfoEmpty() {
        UUID userId = UUID.randomUUID();
        createEnabledPreference(userId, "");

        NotificationRequest req =
                new NotificationRequest(userId, "Subj", "Body");

        assertThrows(IllegalStateException.class,
                () -> notificationService.sendNotification(req));

        assertTrue(notificationRepository.findAll().isEmpty());
        var testMailSender = (TestConfig.TestMailSender) mailSender;
        assertTrue(testMailSender.getSentMessages().isEmpty());
    }

    @Test
    void changeNotificationPreferenceStatus_shouldCreateNewDisabledPreference_whenNotExists() {
        UUID userId = UUID.randomUUID();
        assertTrue(preferenceRepository.findByUserId(userId).isEmpty());

        NotificationPreference created =
                notificationService.changeNotificationPreferenceStatus(userId, true);

        assertNotNull(created.getId());
        assertEquals(userId, created.getUserId());
        assertFalse(created.isEnabled());
        assertEquals("", created.getContactInfo());

        NotificationPreference fromDb =
                preferenceRepository.findByUserId(userId).orElseThrow();
        assertFalse(fromDb.isEnabled());
        assertEquals("", fromDb.getContactInfo());
    }

    @Test
    void scheduleNotification_shouldThrow_whenPreferenceDisabled() {
        UUID userId = UUID.randomUUID();
        createDisabledPreference(userId, "u@example.com");

        NotificationRequest req =
                new NotificationRequest(userId, "Remind", "Later");

        assertThrows(DisableNotificationPreferenceException.class,
                () -> notificationService.scheduleNotification(req, LocalDateTime.now().plusMinutes(10)));

        assertTrue(notificationRepository.findAll().isEmpty());
    }

    @Test
    void scheduleEventReminders_shouldUseDefaultOffsets_whenOffsetsNullOrEmpty() {
        UUID userId = UUID.randomUUID();
        createEnabledPreference(userId, "u@example.com");

        LocalDateTime eventStart = LocalDateTime.now().plusHours(30);

        EventReminderRequest req = EventReminderRequest.builder()
                .userId(userId)
                .subject("Event")
                .body("Don't forget")
                .eventStart(eventStart)
                .offsetsMinutes(null)
                .build();

        List<Notification> scheduled = notificationService.scheduleEventReminders(req);

        assertEquals(2, scheduled.size());
        List<LocalDateTime> scheduledTimes =
                scheduled.stream().map(Notification::getScheduledAt).toList();

        assertTrue(scheduledTimes.contains(eventStart.minusMinutes(1440)));
        assertTrue(scheduledTimes.contains(eventStart.minusMinutes(120)));
    }

    @Test
    void scheduleEventReminders_shouldSkipPastOffsets() {
        UUID userId = UUID.randomUUID();
        createEnabledPreference(userId, "u@example.com");

        LocalDateTime eventStart = LocalDateTime.now().plusMinutes(20);

        EventReminderRequest req = EventReminderRequest.builder()
                .userId(userId)
                .subject("Event")
                .body("Soon")
                .eventStart(eventStart)
                .offsetsMinutes(List.of(10, 1440))
                .build();

        List<Notification> scheduled = notificationService.scheduleEventReminders(req);

        assertEquals(1, scheduled.size());
        Notification n = scheduled.get(0);
        assertEquals(eventStart.minusMinutes(10), n.getScheduledAt());
    }

    @Test
    void sendReminder_shouldSendImmediatelyAndMarkSucceeded() {
        UUID userId = UUID.randomUUID();
        createEnabledPreference(userId, "u@example.com");

        NotificationRequest req =
                new NotificationRequest(userId, "Now", "Immediate reminder");

        Notification result = notificationService.sendReminder(req);

        assertNotNull(result.getId());
        assertEquals(NotificationStatus.SUCCEEDED, result.getStatus());
        assertNull(result.getLastError());

        Notification fromDb = notificationRepository.findById(result.getId())
                .orElseThrow();
        assertEquals(NotificationStatus.SUCCEEDED, fromDb.getStatus());

        var testMailSender = (TestConfig.TestMailSender) mailSender;
        assertEquals(1, testMailSender.getSentMessages().size());
    }

    @Test
    void sendReminder_shouldMarkFailed_whenSendEmailThrows() {
        UUID userId = UUID.randomUUID();
        createEnabledPreference(userId, "u@example.com");

        var testMailSender = (TestConfig.TestMailSender) mailSender;
        testMailSender.setFailNext(true);

        NotificationRequest req =
                new NotificationRequest(userId, "Now", "Immediate reminder");

        Notification result = notificationService.sendReminder(req);

        assertEquals(NotificationStatus.FAILED, result.getStatus());
        assertNotNull(result.getLastError());

        Notification fromDb = notificationRepository.findById(result.getId())
                .orElseThrow();
        assertEquals(NotificationStatus.FAILED, fromDb.getStatus());
        assertNotNull(fromDb.getLastError());
    }

    @Test
    void processDueNotifications_shouldIncrementAttemptsAndReschedule_whenSendEmailFailsAndAttemptsLessThan3() {
        UUID userId = UUID.randomUUID();
        createEnabledPreference(userId, "");

        LocalDateTime initialScheduledAt = LocalDateTime.now().minusMinutes(5);

        Notification pending = Notification.builder()
                .userId(userId)
                .subject("Due")
                .body("Will fail")
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.PENDING)
                .created(LocalDateTime.now().minusHours(1))
                .scheduledAt(initialScheduledAt)
                .deleted(false)
                .attempts(0)
                .build();

        pending = notificationRepository.save(pending);

        notificationService.processDueNotifications();

        Notification fromDb = notificationRepository.findById(pending.getId())
                .orElseThrow();

        assertEquals(1, fromDb.getAttempts());
        assertEquals(NotificationStatus.PENDING, fromDb.getStatus());
        assertNotNull(fromDb.getLastError());
        assertTrue(fromDb.getScheduledAt().isAfter(initialScheduledAt));
    }

    @Test
    void processDueNotifications_shouldMarkFailed_whenAttemptsReach3() {
        UUID userId = UUID.randomUUID();
        createEnabledPreference(userId, "");

        Notification pending = Notification.builder()
                .userId(userId)
                .subject("Due")
                .body("Will fail permanently")
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.PENDING)
                .created(LocalDateTime.now().minusHours(1))
                .scheduledAt(LocalDateTime.now().minusMinutes(5))
                .deleted(false)
                .attempts(2)
                .build();

        pending = notificationRepository.save(pending);

        notificationService.processDueNotifications();

        Notification fromDb = notificationRepository.findById(pending.getId())
                .orElseThrow();

        assertEquals(3, fromDb.getAttempts());
        assertEquals(NotificationStatus.FAILED, fromDb.getStatus());
        assertNotNull(fromDb.getLastError());
    }

    @Test
    void getById_shouldReturnNotification_whenExists() {
        UUID userId = UUID.randomUUID();
        createEnabledPreference(userId, "u@example.com");

        Notification n = Notification.builder()
                .userId(userId)
                .subject("X")
                .body("Y")
                .type(NotificationType.EMAIL)
                .status(NotificationStatus.SUCCEEDED)
                .created(LocalDateTime.now())
                .deleted(false)
                .build();

        n = notificationRepository.save(n);

        Notification fromService = notificationService.getById(n.getId());

        assertEquals(n.getId(), fromService.getId());
        assertEquals("X", fromService.getSubject());
        assertEquals("Y", fromService.getBody());
    }

    @Test
    void getById_shouldThrow_whenNotExists() {
        UUID randomId = UUID.randomUUID();

        assertThrows(RuntimeException.class,
                () -> notificationService.getById(randomId));
    }
}
