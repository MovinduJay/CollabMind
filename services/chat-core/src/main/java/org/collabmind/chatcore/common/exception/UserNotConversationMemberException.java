package org.collabmind.chatcore.common.exception;

import java.util.UUID;

public class UserNotConversationMemberException extends RuntimeException {

    public UserNotConversationMemberException(UUID userId, UUID conversationId) {
        super("User " + userId + " is not a member of conversation " + conversationId);
    }
}
