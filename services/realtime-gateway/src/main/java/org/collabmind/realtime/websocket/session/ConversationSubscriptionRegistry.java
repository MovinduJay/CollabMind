package org.collabmind.realtime.websocket.session;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ConversationSubscriptionRegistry {

    private final ConcurrentMap<String, Set<String>> sessionsByConversationId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> conversationsBySessionId = new ConcurrentHashMap<>();

    public int subscribe(String conversationId, String sessionId) {
        sessionsByConversationId
                .computeIfAbsent(conversationId, key -> ConcurrentHashMap.newKeySet())
                .add(sessionId);

        conversationsBySessionId
                .computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet())
                .add(conversationId);

        return subscriberCount(conversationId);
    }

    public int unsubscribe(String conversationId, String sessionId) {
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

        return subscriberCount(conversationId);
    }

    public Set<String> removeSessionFromAllConversations(String sessionId) {
        Set<String> conversations = conversationsBySessionId.remove(sessionId);

        if (conversations == null || conversations.isEmpty()) {
            return Set.of();
        }

        Set<String> removedConversations = Set.copyOf(conversations);

        for (String conversationId : removedConversations) {
            Set<String> sessions = sessionsByConversationId.get(conversationId);

            if (sessions != null) {
                sessions.remove(sessionId);

                if (sessions.isEmpty()) {
                    sessionsByConversationId.remove(conversationId);
                }
            }
        }

        return removedConversations;
    }

    public Set<String> getSubscribedSessions(String conversationId) {
        Set<String> sessions = sessionsByConversationId.get(conversationId);

        if (sessions == null || sessions.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(sessions);
    }

    public Set<String> getSubscribedConversations(String sessionId) {
        Set<String> conversations = conversationsBySessionId.get(sessionId);

        if (conversations == null || conversations.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(conversations);
    }

    public boolean isSubscribed(String conversationId, String sessionId) {
        return getSubscribedSessions(conversationId).contains(sessionId);
    }

    public int subscriberCount(String conversationId) {
        Set<String> sessions = sessionsByConversationId.get(conversationId);

        if (sessions == null) {
            return 0;
        }

        return sessions.size();
    }
}
