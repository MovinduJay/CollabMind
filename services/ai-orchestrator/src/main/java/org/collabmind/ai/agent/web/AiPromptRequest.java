package org.collabmind.ai.agent.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.collabmind.ai.agent.domain.AgentType;

import java.util.List;
import java.util.UUID;

public record AiPromptRequest(
        @NotNull UUID conversationId,
        @NotNull UUID userId,
        @NotNull AgentType agentType,
        @NotBlank String message,
        @Valid @Size(max = 20) List<AiContextMessage> contextMessages
) {
}
