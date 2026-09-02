package org.collabmind.chatcore.messaging.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendMessageRequest(
        @NotNull(message = "Sender user ID is required")
        UUID senderId,

        @NotNull(message = "Client message ID is required")
        UUID clientMessageId,

        @NotBlank(message = "Message content is required")
        @Size(max = 4000, message = "Message content must be less than 4000 characters")
        String content
) {
}
