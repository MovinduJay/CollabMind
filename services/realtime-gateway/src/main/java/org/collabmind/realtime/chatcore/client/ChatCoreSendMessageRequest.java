package org.collabmind.realtime.chatcore.client;

import java.util.UUID;

public record ChatCoreSendMessageRequest(
        UUID senderId,
        UUID clientMessageId,
        String content
) {
}
