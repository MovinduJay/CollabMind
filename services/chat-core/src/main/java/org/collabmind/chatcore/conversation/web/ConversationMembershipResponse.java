package org.collabmind.chatcore.conversation.web;

import java.util.UUID;

public record ConversationMembershipResponse(
        UUID conversationId,
        UUID userId,
        boolean member
) {
}
