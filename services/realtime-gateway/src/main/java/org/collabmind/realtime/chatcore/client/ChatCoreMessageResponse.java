package org.collabmind.realtime.chatcore.client;

import java.time.Instant;
import java.util.UUID;

public record ChatCoreMessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        UUID clientMessageId,
        long sequenceNumber,
        String messageType,
        String content,
        String agentType,
        UUID sourceMessageId,
        Instant createdAt
) {
}
