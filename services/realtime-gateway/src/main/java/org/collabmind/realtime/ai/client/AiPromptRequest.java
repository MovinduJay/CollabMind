package org.collabmind.realtime.ai.client;

import java.util.UUID;

public record AiPromptRequest(
        UUID conversationId,
        UUID userId,
        String agentType,
        String message
) {
}
