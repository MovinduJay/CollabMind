package org.collabmind.realtime.websocket.protocol;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ServerEvent(
        String eventId,
        String eventType,
        String conversationId,
        Instant occurredAt,
        Map<String, Object> payload
) {

    public static ServerEvent of(
            String eventType,
            String conversationId,
            Map<String, Object> payload
    ) {
        return new ServerEvent(
                UUID.randomUUID().toString(),
                eventType,
                conversationId,
                Instant.now(),
                payload
        );
    }
}
