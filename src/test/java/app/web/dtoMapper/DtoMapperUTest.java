package app.web.dtoMapper;

import com.example.notification_service.model.Notification;
import com.example.notification_service.model.NotificationPreference;
import com.example.notification_service.model.NotificationStatus;
import com.example.notification_service.model.NotificationType;
import com.example.notification_service.web.dto.NotificationPreferenceResponse;
import com.example.notification_service.web.dto.NotificationResponse;
import com.example.notification_service.web.dto.NotificationTypeRequest;
import com.example.notification_service.web.mapper.DtoMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DtoMapperUTest {

    @Test
    void fromNotificationTypeRequest_shouldMapEmailCorrectly() {
        NotificationType result =
                DtoMapper.fromNotificationTypeRequest(NotificationTypeRequest.EMAIL);

        assertEquals(NotificationType.EMAIL, result);
    }

    @Test
    void fromNotificationPreference_shouldMapAllFieldsCorrectly() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        NotificationPreference preference = NotificationPreference.builder()
                .id(id)
                .userId(userId)
                .type(NotificationType.EMAIL)
                .contactInfo("user@example.com")
                .enabled(true)
                .build();

        NotificationPreferenceResponse response =
                DtoMapper.fromNotificationPreference(preference);

        assertEquals(id, response.getId());
        assertEquals(userId, response.getUserId());
        assertEquals(NotificationType.EMAIL, response.getType());
        assertEquals("user@example.com", response.getContactInfo());
        assertTrue(response.isEnabled());
    }

    @Test
    void fromNotification_shouldMapAllFieldsCorrectly() {
        LocalDateTime now = LocalDateTime.now();

        Notification notification = Notification.builder()
                .subject("Test subject")
                .created(now)
                .status(NotificationStatus.SUCCEEDED)
                .type(NotificationType.EMAIL)
                .build();

        NotificationResponse resp = DtoMapper.fromNotification(notification);

        assertEquals("Test subject", resp.getSubject());
        assertEquals(now, resp.getCreated());
        assertEquals(NotificationStatus.SUCCEEDED, resp.getStatus());
        assertEquals(NotificationType.EMAIL, resp.getType());
    }
}

