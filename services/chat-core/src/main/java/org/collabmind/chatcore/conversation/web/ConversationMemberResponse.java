package org.collabmind.chatcore.conversation.web;

import org.collabmind.chatcore.membership.domain.ConversationMember;

import java.time.Instant;
import java.util.UUID;

public record ConversationMemberResponse(
        UUID conversationId,
        UUID userId,
        String role,
        Instant joinedAt
) {
    public static ConversationMemberResponse from(ConversationMember member) {
        return new ConversationMemberResponse(
                member.getConversationId(),
                member.getUserId(),
                member.getRole().name(),
                member.getJoinedAt()
        );
    }
}
