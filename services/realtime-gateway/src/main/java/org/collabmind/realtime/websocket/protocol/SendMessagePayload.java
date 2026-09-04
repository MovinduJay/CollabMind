package org.collabmind.realtime.websocket.protocol;

import java.util.UUID;

public record SendMessagePayload(
        UUID conversationId,
        UUID clientMessageId,
        String content
) {
}
