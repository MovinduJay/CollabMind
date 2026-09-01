package org.collabmind.ai.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "service", "ai-orchestrator",
                "status", "ok",
                "timestamp", Instant.now().toString()
        );
    }
}
