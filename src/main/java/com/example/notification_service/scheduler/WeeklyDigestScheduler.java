package com.example.notification_service.scheduler;

import com.example.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;

@Slf4j
@Component
public class WeeklyDigestScheduler {

    private final NotificationService notificationService;

    public WeeklyDigestScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 30 17 * * FRI", zone = "Europe/Sofia")
    public void sendWeeklyDigest() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Sofia"));
        LocalDateTime periodEnd = now.toLocalDateTime();
        LocalDateTime periodStart = periodEnd.minusDays(7);

        log.info("Weekly digest: {} -> {}", periodStart, periodEnd);
        notificationService.sendWeeklyDigest(periodStart, periodEnd);
        log.info("Weekly digest sent.");
    }
}

