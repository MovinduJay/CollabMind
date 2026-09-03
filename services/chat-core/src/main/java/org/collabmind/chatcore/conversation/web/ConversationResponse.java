package org.collabmind.chatcore.conversation.web;

import org.collabmind.chatcore.conversation.domain.Conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        String name,
        UUID createdByUserId,
        long memberCount,
        Instant createdAt
) {

    public static ConversationResponse from(Conversation conversation, long memberCount) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getName(),
                conversation.getCreatedByUserId(),
                memberCount,
                conversation.getCreatedAt()
        );
    }
}
