package org.collabmind.ai.audit.web;

import org.collabmind.ai.audit.infrastructure.AiRequestLogRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai/audit-logs")
public class AiRequestLogController {

    private final AiRequestLogRepository repository;

    public AiRequestLogController(AiRequestLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/recent")
    public List<AiRequestLogResponse> recentLogs() {
        return repository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(AiRequestLogResponse::from)
                .toList();
    }
}
