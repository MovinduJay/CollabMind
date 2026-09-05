package org.collabmind.realtime.chatcore.client;

import java.util.UUID;

public record ChatCoreSaveAiMessageRequest(
        UUID requesterUserId,
        UUID clientMessageId,
        UUID sourceMessageId,
        String agentType,
        String content
) {
}
