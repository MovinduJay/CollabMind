package org.collabmind.realtime.ai.client;

import java.time.Instant;
import java.util.UUID;

public record AiPromptResponse(
        UUID conversationId,
        UUID userId,
        String agentType,
        String providerName,
        String primaryProviderName,
        boolean fallbackUsed,
        long latencyMs,
        String response,
        Instant createdAt
) {
}
