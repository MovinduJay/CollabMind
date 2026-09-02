package org.collabmind.chatcore.conversation.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateConversationRequest(
        @NotBlank(message = "Conversation name is required")
        @Size(max = 120, message = "Conversation name must be less than 120 characters")
        String name,

        @NotNull(message = "Creator user ID is required")
        UUID creatorUserId
) {
}
