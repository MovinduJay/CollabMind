package org.collabmind.chatcore.conversation.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateConversationRequest(
        @NotBlank @Size(max = 120) String name
) {
}
