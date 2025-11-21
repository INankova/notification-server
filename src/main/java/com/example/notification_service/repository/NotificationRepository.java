package com.example.notification_service.repository;

import com.example.notification_service.model.Notification;
import com.example.notification_service.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
        SELECT n FROM Notification n WHERE n.userId=:userId AND n.deleted=false
""")
    List<Notification> findAllByUserIdAndDeleted(@Param("userId") UUID userId);

    List<Notification> findAllByStatusAndScheduledAtBefore(NotificationStatus status, LocalDateTime time);
}
