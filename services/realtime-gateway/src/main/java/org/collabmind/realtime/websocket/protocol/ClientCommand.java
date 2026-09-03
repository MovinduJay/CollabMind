package org.collabmind.realtime.websocket.protocol;

import com.fasterxml.jackson.databind.JsonNode;

public record ClientCommand(
        String commandId,
        String commandType,
        String conversationId,
        JsonNode payload
) {
}
