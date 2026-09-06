package org.collabmind.realtime.websocket.protocol;

public record FetchMessagesPayload(
        Long afterSequence,
        Integer limit
) {
}
