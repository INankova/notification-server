package com.example.notification_service.client;

import com.example.notification_service.web.dto.EventSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "event-service", url = "http://localhost:8080/api/v1/events")
public interface EventClient {

    @GetMapping
    ResponseEntity<List<EventSummary>> listBetween(
            @RequestParam("from") String fromIso,
            @RequestParam("to") String toIso
    );
}
