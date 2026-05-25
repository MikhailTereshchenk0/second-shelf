package com.secondshelf.notificationservice.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class NotificationServiceSecurityTestController {

    @GetMapping("/v3/api-docs")
    Map<String, String> openApi() {
        return Map.of("openapi", "3.0.1");
    }

    @GetMapping("/actuator/metrics")
    Map<String, String> metrics() {
        return Map.of("name", "requests.total");
    }

    @GetMapping("/actuator/health/asyncFlow")
    Map<String, String> asyncFlow() {
        return Map.of("status", "UP");
    }

    @GetMapping("/actuator/health/readiness")
    Map<String, String> readiness() {
        return Map.of("status", "UP");
    }
}
