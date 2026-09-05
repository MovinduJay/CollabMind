package org.collabmind.realtime.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.collabmind.realtime.security.InvalidJwtTokenException;
import org.collabmind.realtime.security.JwtTokenService;
import org.collabmind.realtime.websocket.application.ConversationSubscriptionService;
import org.collabmind.realtime.websocket.application.MessageHistoryService;
import org.collabmind.realtime.websocket.application.MessageRelayService;
import org.collabmind.realtime.websocket.application.RealtimeFanoutService;
import org.collabmind.realtime.websocket.application.TypingIndicatorService;
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
import java.util.Set;
import java.util.UUID;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ConnectionRegistry connectionRegistry;
    private final ConversationSubscriptionRegistry subscriptionRegistry;
    private final RealtimeFanoutService fanoutService;
    private final MessageRelayService messageRelayService;
    private final ConversationSubscriptionService subscriptionService;
    private final MessageHistoryService messageHistoryService;
    private final TypingIndicatorService typingIndicatorService;
    private final JwtTokenService jwtTokenService;

    public ChatWebSocketHandler(
            ObjectMapper objectMapper,
            ConnectionRegistry connectionRegistry,
            ConversationSubscriptionRegistry subscriptionRegistry,
            RealtimeFanoutService fanoutService,
            MessageRelayService messageRelayService,
            ConversationSubscriptionService subscriptionService,
            MessageHistoryService messageHistoryService,
            TypingIndicatorService typingIndicatorService,
            JwtTokenService jwtTokenService
    ) {
        this.objectMapper = objectMapper;
        this.connectionRegistry = connectionRegistry;
        this.subscriptionRegistry = subscriptionRegistry;
        this.fanoutService = fanoutService;
        this.messageRelayService = messageRelayService;
        this.subscriptionService = subscriptionService;
        this.messageHistoryService = messageHistoryService;
        this.typingIndicatorService = typingIndicatorService;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractQueryParam(session.getUri(), "token");

        if (token == null || token.isBlank()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing JWT token"));
            return;
        }

        UUID userId;

        try {
            userId = jwtTokenService.extractUserId(token);
        } catch (InvalidJwtTokenException exception) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid JWT token"));
            return;
        }

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
            subscriptionService.subscribe(client, command);
            return;
        }

        if ("UNSUBSCRIBE_CONVERSATION".equalsIgnoreCase(command.commandType())) {
            subscriptionService.unsubscribe(client, command);
            return;
        }

        if ("START_TYPING".equalsIgnoreCase(command.commandType())) {
            typingIndicatorService.startTyping(client, command);
            return;
        }

        if ("STOP_TYPING".equalsIgnoreCase(command.commandType())) {
            typingIndicatorService.stopTyping(client, command);
            return;
        }

        if ("FETCH_MESSAGES_AFTER".equalsIgnoreCase(command.commandType())) {
            messageHistoryService.fetchMessagesAfter(client, command);
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
        cleanupConnection(session, "connection closed");
    }

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception
    ) throws Exception {
        cleanupConnection(session, "transport error");

        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void cleanupConnection(
            WebSocketSession session,
            String reason
    ) {
        connectionRegistry.findBySessionId(session.getId()).ifPresentOrElse(
                client -> {
                    Set<String> conversations = subscriptionRegistry.removeSessionFromAllConversations(session.getId());
                    connectionRegistry.unregister(session.getId());

                    for (String conversationId : conversations) {
                        ServerEvent leftEvent = ServerEvent.of(
                                "USER_LEFT_CONVERSATION",
                                conversationId,
                                Map.of(
                                        "conversationId", conversationId,
                                        "userId", client.userId().toString(),
                                        "sessionId", client.sessionId(),
                                        "reason", reason
                                )
                        );

                        fanoutService.sendToConversation(conversationId, leftEvent);
                    }
                },
                () -> {
                    subscriptionRegistry.removeSessionFromAllConversations(session.getId());
                    connectionRegistry.unregister(session.getId());
                }
        );
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
