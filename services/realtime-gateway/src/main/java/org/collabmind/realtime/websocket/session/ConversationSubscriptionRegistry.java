package org.collabmind.realtime.websocket.session;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ConversationSubscriptionRegistry {

    private final ConcurrentMap<String, Set<String>> sessionsByConversationId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> conversationsBySessionId = new ConcurrentHashMap<>();

    public void subscribe(String conversationId, String sessionId) {
        sessionsByConversationId
                .computeIfAbsent(conversationId, key -> ConcurrentHashMap.newKeySet())
                .add(sessionId);

        conversationsBySessionId
                .computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet())
                .add(conversationId);
    }

    public void unsubscribe(String conversationId, String sessionId) {
        Set<String> sessions = sessionsByConversationId.get(conversationId);

        if (sessions != null) {
            sessions.remove(sessionId);

            if (sessions.isEmpty()) {
                sessionsByConversationId.remove(conversationId);
            }
        }

        Set<String> conversations = conversationsBySessionId.get(sessionId);

        if (conversations != null) {
            conversations.remove(conversationId);

            if (conversations.isEmpty()) {
                conversationsBySessionId.remove(sessionId);
            }
        }
    }

    public void removeSessionFromAllConversations(String sessionId) {
        Set<String> conversations = conversationsBySessionId.remove(sessionId);

        if (conversations == null) {
            return;
        }

        for (String conversationId : conversations) {
            Set<String> sessions = sessionsByConversationId.get(conversationId);

            if (sessions != null) {
                sessions.remove(sessionId);

                if (sessions.isEmpty()) {
                    sessionsByConversationId.remove(conversationId);
                }
            }
        }
    }

    public Set<String> getSubscribedSessions(String conversationId) {
        return sessionsByConversationId.getOrDefault(conversationId, Set.of());
    }

    public int subscriberCount(String conversationId) {
        return getSubscribedSessions(conversationId).size();
    }
}
