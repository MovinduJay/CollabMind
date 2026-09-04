package org.collabmind.realtime.chatcore.client;

import java.util.UUID;

public record ChatCoreMembershipResponse(
        UUID conversationId,
        UUID userId,
        boolean member
) {
}
