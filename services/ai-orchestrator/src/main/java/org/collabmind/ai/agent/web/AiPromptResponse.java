package org.collabmind.ai.agent.web;

import org.collabmind.ai.agent.domain.AgentType;

import java.time.Instant;
import java.util.UUID;

public record AiPromptResponse(
        UUID conversationId,
        UUID userId,
        AgentType agentType,
        String response,
        Instant createdAt
) {
}
