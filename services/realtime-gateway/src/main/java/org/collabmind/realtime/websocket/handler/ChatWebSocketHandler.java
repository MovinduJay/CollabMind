package org.collabmind.realtime.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.collabmind.realtime.websocket.application.RealtimeFanoutService;
import org.collabmind.realtime.websocket.protocol.ClientCommand;
import org.collabmind.realtime.websocket.protocol.ServerEvent;
import org.collabmind.realtime.websocket.session.ConnectedClient;
import org.collabmind.realtime.websocket.session.ConnectionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ConnectionRegistry connectionRegistry;
    private final RealtimeFanoutService fanoutService;

    public ChatWebSocketHandler(
            ObjectMapper objectMapper,
            ConnectionRegistry connectionRegistry,
            RealtimeFanoutService fanoutService
    ) {
        this.objectMapper = objectMapper;
        this.connectionRegistry = connectionRegistry;
        this.fanoutService = fanoutService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = extractUserId(session.getUri());

        ConnectedClient client = connectionRegistry.register(session, userId);

        ServerEvent connectedEvent = ServerEvent.of(
                "CONNECTED",
                null,
                Map.of(
                        "sessionId", client.sessionId(),
                        "userId", client.userId().toString(),
                        "activeConnections", connectionRegistry.activeConnectionCount()
                )
        );

        fanoutService.sendToClient(client, connectedEvent);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ConnectedClient client = connectionRegistry.findBySessionId(session.getId())
                .orElseThrow(() -> new IllegalStateException("WebSocket session is not registered"));

        ClientCommand command = objectMapper.readValue(message.getPayload(), ClientCommand.class);

        if ("PING".equalsIgnoreCase(command.commandType())) {
            ServerEvent pongEvent = ServerEvent.of(
                    "PONG",
                    command.conversationId(),
                    Map.of(
                            "commandId", command.commandId(),
                            "userId", client.userId().toString()
                    )
            );

            fanoutService.sendToClient(client, pongEvent);
            return;
        }

        if ("BROADCAST_TEST".equalsIgnoreCase(command.commandType())) {
            ServerEvent broadcastEvent = ServerEvent.of(
                    "BROADCAST_TEST",
                    command.conversationId(),
                    Map.of(
                            "commandId", command.commandId(),
                            "fromUserId", client.userId().toString(),
                            "payload", command.payload()
                    )
            );

            fanoutService.broadcast(broadcastEvent);
            return;
        }

        ServerEvent rejectedEvent = ServerEvent.of(
                "COMMAND_REJECTED",
                command.conversationId(),
                Map.of(
                        "commandId", command.commandId(),
                        "reason", "Unsupported command type: " + command.commandType()
                )
        );

        fanoutService.sendToClient(client, rejectedEvent);
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {
        connectionRegistry.unregister(session.getId());
    }

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception
    ) throws Exception {
        connectionRegistry.unregister(session.getId());

        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private UUID extractUserId(URI uri) {
        String userId = extractQueryParam(uri, "userId");

        if (userId == null || userId.isBlank()) {
            return UUID.randomUUID();
        }

        return UUID.fromString(userId);
    }

    private String extractQueryParam(URI uri, String key) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }

        String[] params = uri.getQuery().split("&");

        for (String param : params) {
            String[] pair = param.split("=", 2);

            if (pair.length == 2 && pair[0].equals(key)) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }

        return null;
    }
}
