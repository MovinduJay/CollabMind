package org.collabmind.chatcore.conversation.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateConversationRequest(
        @NotBlank @Size(max = 120) String name,
        UUID creatorUserId
) {
}
