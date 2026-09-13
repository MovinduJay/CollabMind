package org.collabmind.realtime.websocket.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.collabmind.realtime.websocket.protocol.ServerEvent;
import org.collabmind.realtime.websocket.session.ConnectedClient;
import org.collabmind.realtime.websocket.session.ConnectionRegistry;
import org.collabmind.realtime.websocket.session.ConversationSubscriptionRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Service
public class RealtimeFanoutService implements MessageListener {

    private final ConnectionRegistry connections;
    private final ConversationSubscriptionRegistry subscriptions;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final String channel;

    public RealtimeFanoutService(ConnectionRegistry connections,
                                 ConversationSubscriptionRegistry subscriptions,
                                 ObjectMapper objectMapper,
                                 StringRedisTemplate redis,
                                 @Value("${collabmind.redis.realtime-channel:collabmind:realtime}") String channel) {
        this.connections = connections;
        this.subscriptions = subscriptions;
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.channel = channel;
    }

    public void sendToClient(ConnectedClient client, ServerEvent event) {
        WebSocketSession session = client.session();
        if (!session.isOpen()) {
            cleanupClosedClient(client);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(json));
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize server event", e);
        } catch (IOException e) {
            cleanupClosedClient(client);
        }
    }

    public void sendToConversation(String conversationId, ServerEvent event) {
        publish(new FanoutEnvelope(conversationId, null, false, event));
    }

    public void sendToConversationExcept(String conversationId, String excludedSessionId, ServerEvent event) {
        publish(new FanoutEnvelope(conversationId, excludedSessionId, false, event));
    }

    public void broadcast(ServerEvent event) {
        publish(new FanoutEnvelope(null, null, true, event));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            FanoutEnvelope envelope = objectMapper.readValue(message.getBody(), FanoutEnvelope.class);
            if (envelope.broadcast()) {
                connections.getAllClients().forEach(client -> sendToClient(client, envelope.event()));
                return;
            }
            subscriptions.getSubscribedSessions(envelope.conversationId()).stream()
                    .filter(id -> envelope.excludedSessionId() == null || !id.equals(envelope.excludedSessionId()))
                    .map(connections::findBySessionId)
                    .flatMap(java.util.Optional::stream)
                    .forEach(client -> sendToClient(client, envelope.event()));
        } catch (Exception ignored) {
            // Malformed Pub/Sub payloads are isolated from WebSocket delivery.
        }
    }

    private void publish(FanoutEnvelope envelope) {
        try {
            redis.convertAndSend(channel, objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize distributed fanout event", e);
        }
    }

    private void cleanupClosedClient(ConnectedClient client) {
        connections.unregister(client.sessionId());
        subscriptions.removeSessionFromAllConversations(client.sessionId());
    }

    public record FanoutEnvelope(String conversationId, String excludedSessionId, boolean broadcast, ServerEvent event) {}
}
