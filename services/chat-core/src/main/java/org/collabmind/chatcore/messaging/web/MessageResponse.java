package org.collabmind.chatcore.messaging.web;

import org.collabmind.chatcore.messaging.domain.Message;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
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

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getClientMessageId(),
                message.getSequenceNumber(),
                message.getMessageType().name(),
                message.getContent(),
                message.getAgentType(),
                message.getSourceMessageId(),
                message.getCreatedAt()
        );
    }
}
