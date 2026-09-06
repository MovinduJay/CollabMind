package org.collabmind.realtime.ai.client;

public record AiContextMessage(
        long sequenceNumber,
        String messageType,
        String content,
        String agentType
) {
}
