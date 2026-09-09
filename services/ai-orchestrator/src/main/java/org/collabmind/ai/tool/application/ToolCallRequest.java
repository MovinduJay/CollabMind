package org.collabmind.ai.tool.application;

import java.util.UUID;

public record ToolCallRequest(
        UUID conversationId,
        UUID userId,
        String toolName,
        String userMessage,
        String contextSummary
) {
}
