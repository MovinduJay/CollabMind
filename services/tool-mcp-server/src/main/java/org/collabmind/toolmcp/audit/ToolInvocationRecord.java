package org.collabmind.toolmcp.audit;

import java.time.Instant;
import java.util.UUID;

public record ToolInvocationRecord(
        String invocationId,
        String jsonRpcId,
        String method,
        String toolName,
        boolean success,
        String errorMessage,
        long latencyMs,
        Instant createdAt
) {
    public static ToolInvocationRecord success(
            String jsonRpcId,
            String method,
            String toolName,
            long latencyMs
    ) {
        return new ToolInvocationRecord(
                UUID.randomUUID().toString(),
                jsonRpcId,
                method,
                toolName,
                true,
                null,
                latencyMs,
                Instant.now()
        );
    }

    public static ToolInvocationRecord failed(
            String jsonRpcId,
            String method,
            String toolName,
            String errorMessage,
            long latencyMs
    ) {
        return new ToolInvocationRecord(
                UUID.randomUUID().toString(),
                jsonRpcId,
                method,
                toolName,
                false,
                errorMessage,
                latencyMs,
                Instant.now()
        );
    }
}
