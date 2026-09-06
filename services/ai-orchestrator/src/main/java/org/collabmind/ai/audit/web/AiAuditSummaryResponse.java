package org.collabmind.ai.audit.web;

import java.time.Instant;
import java.util.Map;

public record AiAuditSummaryResponse(
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        double successRate,
        double averageLatencyMs,
        long maxLatencyMs,
        Map<String, Long> requestsByProvider,
        Map<String, Long> requestsByAgentType,
        Instant generatedAt
) {
}
