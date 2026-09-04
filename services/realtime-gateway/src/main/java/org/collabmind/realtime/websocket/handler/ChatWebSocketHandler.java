package org.collabmind.realtime.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.collabmind.realtime.websocket.application.MessageRelayService;
import org.collabmind.realtime.websocket.application.RealtimeFanoutService;
import org.collabmind.realtime.websocket.protocol.ClientCommand;
import org.collabmind.realtime.websocket.protocol.ServerEvent;
import org.collabmind.realtime.websocket.session.ConnectedClient;
import org.collabmind.realtime.websocket.session.ConnectionRegistry;
import org.collabmind.realtime.websocket.session.ConversationSubscriptionRegistry;
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
    private final ConversationSubscriptionRegistry subscriptionRegistry;
    private final RealtimeFanoutService fanoutService;
    private final MessageRelayService messageRelayService;

    public ChatWebSocketHandler(
            ObjectMapper objectMapper,
            ConnectionRegistry connectionRegistry,
            ConversationSubscriptionRegistry subscriptionRegistry,
            RealtimeFanoutService fanoutService,
            MessageRelayService messageRelayService
    ) {
        this.objectMapper = objectMapper;
        this.connectionRegistry = connectionRegistry;
        this.subscriptionRegistry = subscriptionRegistry;
        this.fanoutService = fanoutService;
        this.messageRelayService = messageRelayService;
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

        if ("SUBSCRIBE_CONVERSATION".equalsIgnoreCase(command.commandType())) {
            subscriptionRegistry.subscribe(command.conversationId(), client.sessionId());

            ServerEvent subscribedEvent = ServerEvent.of(
                    "SUBSCRIBED_CONVERSATION",
                    command.conversationId(),
                    Map.of(
                            "commandId", command.commandId(),
                            "conversationId", command.conversationId(),
                            "subscriberCount", subscriptionRegistry.subscriberCount(command.conversationId())
                    )
            );

            fanoutService.sendToClient(client, subscribedEvent);
            return;
        }

        if ("UNSUBSCRIBE_CONVERSATION".equalsIgnoreCase(command.commandType())) {
            subscriptionRegistry.unsubscribe(command.conversationId(), client.sessionId());

            ServerEvent unsubscribedEvent = ServerEvent.of(
                    "UNSUBSCRIBED_CONVERSATION",
                    command.conversationId(),
                    Map.of(
                            "commandId", command.commandId(),
                            "conversationId", command.conversationId()
                    )
            );

            fanoutService.sendToClient(client, unsubscribedEvent);
            return;
        }

        if ("SEND_MESSAGE".equalsIgnoreCase(command.commandType())) {
            messageRelayService.relaySendMessage(client, command);
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
        subscriptionRegistry.removeSessionFromAllConversations(session.getId());
    }

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception
    ) throws Exception {
        connectionRegistry.unregister(session.getId());
        subscriptionRegistry.removeSessionFromAllConversations(session.getId());

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
