package org.collabmind.toolmcp.audit;

import java.time.Instant;
import java.util.Map;

public record ToolAuditSummary(
        long totalInvocations,
        long successfulInvocations,
        long failedInvocations,
        double successRate,
        double averageLatencyMs,
        long maxLatencyMs,
        Map<String, Long> invocationsByTool,
        Instant generatedAt
) {
}
