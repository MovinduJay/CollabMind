package org.collabmind.realtime.websocket.session;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ConnectionRegistry {

    private final ConcurrentMap<String, ConnectedClient> clientsBySessionId = new ConcurrentHashMap<>();

    public ConnectedClient register(
            WebSocketSession session,
            UUID userId,
            String jwtToken
    ) {
        ConnectedClient client = new ConnectedClient(
                session.getId(),
                userId,
                jwtToken,
                session,
                Instant.now()
        );

        clientsBySessionId.put(session.getId(), client);

        return client;
    }

    public Optional<ConnectedClient> findBySessionId(String sessionId) {
        return Optional.ofNullable(clientsBySessionId.get(sessionId));
    }

    public Collection<ConnectedClient> getAllClients() {
        return clientsBySessionId.values();
    }

    public void unregister(String sessionId) {
        clientsBySessionId.remove(sessionId);
    }

    public int activeConnectionCount() {
        return clientsBySessionId.size();
    }
}
