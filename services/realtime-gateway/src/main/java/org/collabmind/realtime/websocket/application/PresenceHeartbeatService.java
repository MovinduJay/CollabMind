package org.collabmind.realtime.websocket.application;

import org.collabmind.realtime.websocket.session.ConnectionRegistry;
import org.collabmind.realtime.websocket.session.ConversationSubscriptionRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PresenceHeartbeatService {
    private final ConnectionRegistry connections;
    private final ConversationSubscriptionRegistry subscriptions;

    public PresenceHeartbeatService(ConnectionRegistry connections, ConversationSubscriptionRegistry subscriptions) {
        this.connections = connections;
        this.subscriptions = subscriptions;
    }

    @Scheduled(fixedDelay = 30_000)
    public void refreshActiveSessions() {
        connections.getAllClients().stream()
                .filter(client -> client.session().isOpen())
                .forEach(client -> subscriptions.heartbeat(client.sessionId(), client.userId()));
    }
}
