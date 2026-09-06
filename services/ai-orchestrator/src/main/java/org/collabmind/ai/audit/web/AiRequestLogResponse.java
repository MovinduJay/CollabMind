package org.collabmind.ai.audit.web;

import org.collabmind.ai.audit.domain.AiRequestLog;

import java.time.Instant;
import java.util.UUID;

public record AiRequestLogResponse(
        UUID id,
        UUID conversationId,
        UUID userId,
        String agentType,
        String providerName,
        long latencyMs,
        boolean success,
        String errorMessage,
        Instant createdAt
) {

    public static AiRequestLogResponse from(AiRequestLog log) {
        return new AiRequestLogResponse(
                log.getId(),
                log.getConversationId(),
                log.getUserId(),
                log.getAgentType().name(),
                log.getProviderName(),
                log.getLatencyMs(),
                log.isSuccess(),
                log.getErrorMessage(),
                log.getCreatedAt()
        );
    }
}
