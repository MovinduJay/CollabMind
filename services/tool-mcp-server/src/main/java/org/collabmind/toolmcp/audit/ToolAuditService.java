package org.collabmind.toolmcp.audit;

import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

@Service
public class ToolAuditService {

    private static final int MAX_RECENT_RECORDS = 200;
    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;

    private final Deque<ToolInvocationRecord> recentRecords = new ConcurrentLinkedDeque<>();

    public void recordSuccess(
            String jsonRpcId,
            String method,
            String toolName,
            long latencyMs
    ) {
        addRecord(ToolInvocationRecord.success(
                jsonRpcId,
                method,
                normalizeToolName(toolName),
                latencyMs
        ));
    }

    public void recordFailure(
            String jsonRpcId,
            String method,
            String toolName,
            String errorMessage,
            long latencyMs
    ) {
        addRecord(ToolInvocationRecord.failed(
                jsonRpcId,
                method,
                normalizeToolName(toolName),
                errorMessage,
                latencyMs
        ));
    }

    public List<ToolInvocationRecord> recent(int limit) {
        return recentRecords
                .stream()
                .limit(safeLimit(limit))
                .toList();
    }

    public ToolAuditSummary summary() {
        List<ToolInvocationRecord> snapshot = recentRecords.stream().toList();

        long total = snapshot.size();
        long successful = snapshot.stream().filter(ToolInvocationRecord::success).count();
        long failed = total - successful;

        double successRate = total == 0
                ? 0.0
                : (successful * 100.0) / total;

        double averageLatencyMs = snapshot
                .stream()
                .mapToLong(ToolInvocationRecord::latencyMs)
                .average()
                .orElse(0.0);

        long maxLatencyMs = snapshot
                .stream()
                .mapToLong(ToolInvocationRecord::latencyMs)
                .max()
                .orElse(0);

        Map<String, Long> invocationsByTool = snapshot
                .stream()
                .collect(Collectors.groupingBy(
                        ToolInvocationRecord::toolName,
                        Collectors.counting()
                ));

        return new ToolAuditSummary(
                total,
                successful,
                failed,
                successRate,
                averageLatencyMs,
                maxLatencyMs,
                invocationsByTool,
                java.time.Instant.now()
        );
    }

    private void addRecord(ToolInvocationRecord record) {
        recentRecords.addFirst(record);

        while (recentRecords.size() > MAX_RECENT_RECORDS) {
            recentRecords.pollLast();
        }
    }

    private int safeLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private String normalizeToolName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "unknown";
        }

        return toolName.trim();
    }
}
