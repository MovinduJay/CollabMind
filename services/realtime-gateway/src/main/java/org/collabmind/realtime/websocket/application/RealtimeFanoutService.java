package org.collabmind.realtime.websocket.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.collabmind.realtime.websocket.protocol.ServerEvent;
import org.collabmind.realtime.websocket.session.ConnectedClient;
import org.collabmind.realtime.websocket.session.ConnectionRegistry;
import org.collabmind.realtime.websocket.session.ConversationSubscriptionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Service
public class RealtimeFanoutService {

    private final ConnectionRegistry connectionRegistry;
    private final ConversationSubscriptionRegistry subscriptionRegistry;
    private final ObjectMapper objectMapper;

    public RealtimeFanoutService(
            ConnectionRegistry connectionRegistry,
            ConversationSubscriptionRegistry subscriptionRegistry,
            ObjectMapper objectMapper
    ) {
        this.connectionRegistry = connectionRegistry;
        this.subscriptionRegistry = subscriptionRegistry;
        this.objectMapper = objectMapper;
    }

    public void sendToClient(ConnectedClient client, ServerEvent event) {
        WebSocketSession session = client.session();

        if (!session.isOpen()) {
            connectionRegistry.unregister(client.sessionId());
            subscriptionRegistry.removeSessionFromAllConversations(client.sessionId());
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);

            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize server event", exception);
        } catch (IOException exception) {
            connectionRegistry.unregister(client.sessionId());
            subscriptionRegistry.removeSessionFromAllConversations(client.sessionId());
        }
    }

    public void sendToConversation(String conversationId, ServerEvent event) {
        subscriptionRegistry.getSubscribedSessions(conversationId)
                .stream()
                .map(connectionRegistry::findBySessionId)
                .flatMap(java.util.Optional::stream)
                .forEach(client -> sendToClient(client, event));
    }

    public void sendToConversationExcept(
            String conversationId,
            String excludedSessionId,
            ServerEvent event
    ) {
        subscriptionRegistry.getSubscribedSessions(conversationId)
                .stream()
                .filter(sessionId -> !sessionId.equals(excludedSessionId))
                .map(connectionRegistry::findBySessionId)
                .flatMap(java.util.Optional::stream)
                .forEach(client -> sendToClient(client, event));
    }

    public void broadcast(ServerEvent event) {
        connectionRegistry.getAllClients()
                .forEach(client -> sendToClient(client, event));
    }
}
