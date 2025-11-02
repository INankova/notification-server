package com.example.notification_service.repository;

import com.example.notification_service.model.DigestSendLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface DigestSendLogRepository extends JpaRepository<DigestSendLog, UUID> {

    boolean existsByUserIdAndPeriodStartAndPeriodEnd(UUID userId,
                                                     LocalDateTime periodStart,
                                                     LocalDateTime periodEnd);
}

