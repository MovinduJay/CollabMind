package org.collabmind.ai.agent.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.collabmind.ai.agent.domain.AgentType;

import java.util.UUID;

public record AiPromptRequest(
        @NotNull UUID conversationId,
        @NotNull UUID userId,
        @NotNull AgentType agentType,
        @NotBlank String message
) {
}
