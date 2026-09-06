package org.collabmind.ai.audit.web;

import org.collabmind.ai.audit.domain.AiRequestLog;
import org.collabmind.ai.audit.infrastructure.AiRequestLogRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @GetMapping("/summary")
    public AiAuditSummaryResponse summary() {
        List<AiRequestLog> logs = repository.findAll();

        long totalRequests = logs.size();

        long successfulRequests = logs.stream()
                .filter(AiRequestLog::isSuccess)
                .count();

        long failedRequests = totalRequests - successfulRequests;

        double successRate = totalRequests == 0
                ? 0.0
                : (successfulRequests * 100.0) / totalRequests;

        double averageLatencyMs = logs.stream()
                .mapToLong(AiRequestLog::getLatencyMs)
                .average()
                .orElse(0.0);

        long maxLatencyMs = logs.stream()
                .mapToLong(AiRequestLog::getLatencyMs)
                .max()
                .orElse(0L);

        Map<String, Long> requestsByProvider = logs.stream()
                .collect(Collectors.groupingBy(
                        AiRequestLog::getProviderName,
                        Collectors.counting()
                ));

        Map<String, Long> requestsByAgentType = logs.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getAgentType().name(),
                        Collectors.counting()
                ));

        return new AiAuditSummaryResponse(
                totalRequests,
                successfulRequests,
                failedRequests,
                successRate,
                averageLatencyMs,
                maxLatencyMs,
                requestsByProvider,
                requestsByAgentType,
                Instant.now()
        );
    }
}
