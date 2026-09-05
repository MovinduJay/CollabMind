package org.collabmind.ai.agent.web;

public record AiContextMessage(
        long sequenceNumber,
        String messageType,
        String content,
        String agentType
) {
}
