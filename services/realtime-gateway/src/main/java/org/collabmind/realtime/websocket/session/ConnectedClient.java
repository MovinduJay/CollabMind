package org.collabmind.realtime.websocket.session;

import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.UUID;

public record ConnectedClient(
        String sessionId,
        UUID userId,
        String jwtToken,
        WebSocketSession session,
        Instant connectedAt
) {
}
