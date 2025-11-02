package com.example.notification_service.repository;

import com.example.notification_service.model.NotificationPreference;
import com.example.notification_service.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    Optional<NotificationPreference> findByUserId(UUID uuid);

    List<NotificationPreference> findAllByEnabledTrueAndType(NotificationType type);
}
