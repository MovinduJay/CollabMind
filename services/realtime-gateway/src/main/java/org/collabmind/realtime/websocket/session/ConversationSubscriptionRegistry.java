package org.collabmind.realtime.websocket.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;

@Component
public class ConversationSubscriptionRegistry {

    private static final String ROOM_PREFIX = "realtime:room:";
    private static final String SESSION_PREFIX = "realtime:session:";

    private final ConcurrentMap<String, Set<String>> localSessionsByConversation = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> localConversationsBySession = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final String instanceId;
    private final Duration sessionTtl;

    public ConversationSubscriptionRegistry(
            StringRedisTemplate redis,
            @Value("${collabmind.instance-id}") String instanceId,
            @Value("${collabmind.redis.session-ttl-seconds:90}") long sessionTtlSeconds
    ) {
        this.redis = redis;
        this.instanceId = instanceId;
        this.sessionTtl = Duration.ofSeconds(sessionTtlSeconds);
    }

    public int subscribe(String conversationId, String sessionId, UUID userId) {
        localSessionsByConversation.computeIfAbsent(conversationId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
        localConversationsBySession.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(conversationId);

        String member = distributedSessionId(sessionId);
        redis.opsForSet().add(roomKey(conversationId), member);
        redis.expire(roomKey(conversationId), sessionTtl.multipliedBy(2));
        redis.opsForValue().set(sessionKey(member), userId.toString(), sessionTtl);
        return subscriberCount(conversationId);
    }

    public void heartbeat(String sessionId, UUID userId) {
        String member = distributedSessionId(sessionId);
        if (localConversationsBySession.containsKey(sessionId)) {
            redis.opsForValue().set(sessionKey(member), userId.toString(), sessionTtl);
            localConversationsBySession.get(sessionId)
                    .forEach(conversationId -> redis.expire(roomKey(conversationId), sessionTtl.multipliedBy(2)));
        }
    }

    public int unsubscribe(String conversationId, String sessionId) {
        removeLocal(conversationId, sessionId);
        String member = distributedSessionId(sessionId);
        redis.opsForSet().remove(roomKey(conversationId), member);
        if (!localConversationsBySession.containsKey(sessionId)) {
            redis.delete(sessionKey(member));
        }
        return subscriberCount(conversationId);
    }

    public Set<String> removeSessionFromAllConversations(String sessionId) {
        Set<String> conversations = localConversationsBySession.remove(sessionId);
        if (conversations == null) return Set.of();
        Set<String> snapshot = Set.copyOf(conversations);
        String member = distributedSessionId(sessionId);
        snapshot.forEach(id -> {
            Set<String> local = localSessionsByConversation.get(id);
            if (local != null) {
                local.remove(sessionId);
                if (local.isEmpty()) localSessionsByConversation.remove(id);
            }
            redis.opsForSet().remove(roomKey(id), member);
        });
        redis.delete(sessionKey(member));
        return snapshot;
    }

    public Set<String> getSubscribedSessions(String conversationId) {
        Set<String> sessions = localSessionsByConversation.get(conversationId);
        return sessions == null ? Set.of() : new HashSet<>(sessions);
    }

    public Set<String> getSubscribedConversations(String sessionId) {
        Set<String> conversations = localConversationsBySession.get(sessionId);
        return conversations == null ? Set.of() : new HashSet<>(conversations);
    }

    public boolean isSubscribed(String conversationId, String sessionId) {
        return getSubscribedSessions(conversationId).contains(sessionId);
    }

    public int subscriberCount(String conversationId) {
        Set<String> members = redis.opsForSet().members(roomKey(conversationId));
        if (members == null) return 0;
        long active = 0;
        for (String member : members) {
            if (Boolean.TRUE.equals(redis.hasKey(sessionKey(member)))) {
                active++;
            } else {
                redis.opsForSet().remove(roomKey(conversationId), member);
            }
        }
        return Math.toIntExact(active);
    }

    public Set<String> activeUserIds(String conversationId) {
        Set<String> members = redis.opsForSet().members(roomKey(conversationId));
        if (members == null) return Set.of();
        Set<String> users = new HashSet<>();
        for (String member : members) {
            String userId = redis.opsForValue().get(sessionKey(member));
            if (userId != null) users.add(userId);
        }
        return users;
    }

    private void removeLocal(String conversationId, String sessionId) {
        Set<String> sessions = localSessionsByConversation.get(conversationId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) localSessionsByConversation.remove(conversationId);
        }
        Set<String> conversations = localConversationsBySession.get(sessionId);
        if (conversations != null) {
            conversations.remove(conversationId);
            if (conversations.isEmpty()) localConversationsBySession.remove(sessionId);
        }
    }

    private String distributedSessionId(String sessionId) { return instanceId + ":" + sessionId; }
    private String roomKey(String conversationId) { return ROOM_PREFIX + conversationId + ":sessions"; }
    private String sessionKey(String member) { return SESSION_PREFIX + member; }
}
