package org.collabmind.realtime.chatcore.client;

import java.util.UUID;

public record ChatCoreSendMessageRequest(
        UUID clientMessageId,
        String content
) {
}
