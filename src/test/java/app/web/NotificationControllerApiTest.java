package app.web;

import com.example.notification_service.model.Notification;
import com.example.notification_service.model.NotificationPreference;
import com.example.notification_service.model.NotificationStatus;
import com.example.notification_service.model.NotificationType;
import com.example.notification_service.service.NotificationService;
import com.example.notification_service.web.NotificationController;
import com.example.notification_service.web.dto.EventReminderRequest;
import com.example.notification_service.web.dto.NotificationPreferenceResponse;
import com.example.notification_service.web.dto.NotificationRequest;
import com.example.notification_service.web.dto.NotificationResponse;
import com.example.notification_service.web.dto.NotificationScheduleRequest;
import com.example.notification_service.web.dto.UpsertNotificationPreference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerApiTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    @Test
    void upsertNotificationPreference_shouldReturnCreatedAndMappedBody() {
        UUID userId = UUID.randomUUID();

        UpsertNotificationPreference req = new UpsertNotificationPreference();
        req.setUserId(userId);
        req.setNotificationEnabled(true);
        req.setContactInfo("user@example.com");
        req.setType(null);

        NotificationPreference pref = NotificationPreference.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .contactInfo("user@example.com")
                .enabled(true)
                .type(NotificationType.EMAIL)
                .build();

        when(notificationService.upsertPreference(req)).thenReturn(pref);

        ResponseEntity<NotificationPreferenceResponse> response =
                notificationController.upsertNotificationPreference(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        NotificationPreferenceResponse body = response.getBody();
        assertEquals(pref.getId(), body.getId());
        assertEquals(pref.getUserId(), body.getUserId());
        assertEquals(pref.getContactInfo(), body.getContactInfo());
        assertEquals(pref.isEnabled(), body.isEnabled());
        assertEquals(pref.getType(), body.getType());

        verify(notificationService).upsertPreference(req);
    }

    @Test
    void getUserNotificationPreference_shouldReturnOkAndMappedBody() {
        UUID userId = UUID.randomUUID();

        NotificationPreference pref = NotificationPreference.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .contactInfo("user@example.com")
                .enabled(true)
                .type(NotificationType.EMAIL)
                .build();

        when(notificationService.findPreferenceByUserId(userId))
                .thenReturn(java.util.Optional.of(pref));

        ResponseEntity<NotificationPreferenceResponse> response =
                notificationController.getUserNotificationPreference(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        NotificationPreferenceResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(pref.getId(), body.getId());
        assertEquals(pref.getUserId(), body.getUserId());
        assertEquals(pref.getContactInfo(), body.getContactInfo());
        assertEquals(pref.isEnabled(), body.isEnabled());
        assertEquals(pref.getType(), body.getType());

        verify(notificationService).findPreferenceByUserId(userId);
    }


    @Test
    void sendNotification_shouldReturnCreatedAndMappedNotification() {
        UUID userId = UUID.randomUUID();
        NotificationRequest req = new NotificationRequest(userId, "Subj", "Body");

        LocalDateTime now = LocalDateTime.now();

        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .subject("Subj")
                .body("Body")
                .created(now)
                .status(NotificationStatus.SUCCEEDED)
                .type(NotificationType.EMAIL)
                .build();

        when(notificationService.sendNotification(req)).thenReturn(notification);

        ResponseEntity<NotificationResponse> response =
                notificationController.sendNotification(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        NotificationResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Subj", body.getSubject());
        assertEquals(now, body.getCreated());
        assertEquals(NotificationStatus.SUCCEEDED, body.getStatus());
        assertEquals(NotificationType.EMAIL, body.getType());

        verify(notificationService).sendNotification(req);
    }

    @Test
    void getNotifications_shouldReturnOkAndMappedList() {
        UUID userId = UUID.randomUUID();
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(5);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);

        Notification n1 = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .subject("Subj1")
                .created(t1)
                .status(NotificationStatus.SUCCEEDED)
                .type(NotificationType.EMAIL)
                .build();

        Notification n2 = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .subject("Subj2")
                .created(t2)
                .status(NotificationStatus.FAILED)
                .type(NotificationType.EMAIL)
                .build();

        when(notificationService.getNotifications(userId))
                .thenReturn(List.of(n1, n2));

        ResponseEntity<List<NotificationResponse>> response =
                notificationController.getNotifications(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<NotificationResponse> body = response.getBody();
        assertNotNull(body);
        assertEquals(2, body.size());
        assertEquals("Subj1", body.get(0).getSubject());
        assertEquals("Subj2", body.get(1).getSubject());

        verify(notificationService).getNotifications(userId);
    }

    @Test
    void changeNotificationPreference_shouldReturnOkAndMappedBody() {
        UUID userId = UUID.randomUUID();

        NotificationPreference pref = NotificationPreference.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .enabled(false)
                .contactInfo("user@example.com")
                .type(NotificationType.EMAIL)
                .build();

        when(notificationService.changeNotificationPreferenceStatus(userId, true))
                .thenReturn(pref);

        ResponseEntity<NotificationPreferenceResponse> response =
                notificationController.changeNotificationPreference(userId, true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        NotificationPreferenceResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(pref.getId(), body.getId());
        assertEquals(pref.getUserId(), body.getUserId());
        assertEquals(pref.isEnabled(), body.isEnabled());
        assertEquals(pref.getContactInfo(), body.getContactInfo());
        assertEquals(pref.getType(), body.getType());

        verify(notificationService).changeNotificationPreferenceStatus(userId, true);
    }

    @Test
    void clearPreviousNotifications_shouldCallServiceAndReturnOk() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<Void> response =
                notificationController.clearPreviousNotifications(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody());
        verify(notificationService).clearNotifications(userId);
    }

    @Test
    void schedule_shouldCreateScheduledNotificationAndReturnCreated() {
        UUID userId = UUID.randomUUID();
        LocalDateTime scheduledAt = LocalDateTime.now().plusHours(1);

        NotificationScheduleRequest req = new NotificationScheduleRequest();
        req.setUserId(userId);
        req.setSubject("Remind");
        req.setBody("Body");
        req.setScheduledAt(scheduledAt);

        Notification n = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .subject("Remind")
                .body("Body")
                .scheduledAt(scheduledAt)
                .status(NotificationStatus.PENDING)
                .type(NotificationType.EMAIL)
                .build();

        when(notificationService.scheduleNotification(
                any(NotificationRequest.class),
                eq(scheduledAt))
        ).thenReturn(n);

        ResponseEntity<NotificationResponse> response =
                notificationController.schedule(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        NotificationResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Remind", body.getSubject());
        assertEquals(NotificationStatus.PENDING, body.getStatus());

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).scheduleNotification(captor.capture(), eq(scheduledAt));

        NotificationRequest usedReq = captor.getValue();
        assertEquals(userId, usedReq.getUserId());
        assertEquals("Remind", usedReq.getSubject());
        assertEquals("Body", usedReq.getBody());
    }

    @Test
    void scheduleEventReminders_shouldReturnCreatedAndMappedList() {
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        EventReminderRequest req = new EventReminderRequest();
        req.setUserId(userId);
        req.setSubject("Event");
        req.setBody("Body");
        req.setEventStart(now.plusDays(1));
        req.setOffsetsMinutes(List.of(60, 120));

        Notification n1 = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .subject("Event")
                .status(NotificationStatus.PENDING)
                .type(NotificationType.EMAIL)
                .scheduledAt(now.plusMinutes(60))
                .build();

        Notification n2 = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .subject("Event")
                .status(NotificationStatus.PENDING)
                .type(NotificationType.EMAIL)
                .scheduledAt(now.plusMinutes(120))
                .build();

        when(notificationService.scheduleEventReminders(req))
                .thenReturn(List.of(n1, n2));

        ResponseEntity<List<NotificationResponse>> response =
                notificationController.scheduleEventReminders(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        List<NotificationResponse> body = response.getBody();
        assertNotNull(body);
        assertEquals(2, body.size());
        assertEquals("Event", body.get(0).getSubject());
        assertEquals("Event", body.get(1).getSubject());

        verify(notificationService).scheduleEventReminders(req);
    }

    @Test
    void getById_shouldReturnOkAndMappedNotification() {
        UUID id = UUID.randomUUID();

        Notification n = Notification.builder()
                .id(id)
                .subject("Test")
                .created(LocalDateTime.now())
                .status(NotificationStatus.SUCCEEDED)
                .type(NotificationType.EMAIL)
                .build();

        when(notificationService.getById(id)).thenReturn(n);

        ResponseEntity<NotificationResponse> response =
                notificationController.getById(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        NotificationResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Test", body.getSubject());
        assertEquals(NotificationStatus.SUCCEEDED, body.getStatus());
        assertEquals(NotificationType.EMAIL, body.getType());

        verify(notificationService).getById(id);
    }
}
