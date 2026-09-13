package org.collabmind.chatcore.conversation.application;

import org.collabmind.chatcore.conversation.infrastructure.ConversationRepository;
import org.collabmind.chatcore.membership.infrastructure.ConversationMemberRepository;
import org.collabmind.chatcore.messaging.infrastructure.MessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class RoomActivityService {
    private static final String DEADLINES = "rooms:expiry-deadlines";
    private final StringRedisTemplate redis;
    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final Duration ttl;

    public RoomActivityService(StringRedisTemplate redis, ConversationRepository conversations,
            ConversationMemberRepository members, MessageRepository messages,
            @Value("${collabmind.rooms.ttl:PT24H}") Duration ttl) {
        this.redis = redis; this.conversations = conversations; this.members = members; this.messages = messages; this.ttl = ttl;
    }

    public void touch(UUID conversationId) {
        double deadline = Instant.now().plus(ttl).toEpochMilli();
        redis.opsForZSet().add(DEADLINES, conversationId.toString(), deadline);
        redis.opsForValue().set("room:active:" + conversationId, "1", ttl);
    }

    @Scheduled(fixedDelayString = "${collabmind.rooms.expiry-scan-ms:60000}")
    @Transactional
    public void expireInactiveRooms() {
        Set<String> expired = redis.opsForZSet().rangeByScore(DEADLINES, 0, Instant.now().toEpochMilli(), 0, 100);
        if (expired == null) return;
        for (String value : expired) {
            UUID id = UUID.fromString(value);
            if (Boolean.TRUE.equals(redis.hasKey("room:active:" + id))) continue;
            messages.deleteByConversationId(id);
            members.deleteByConversationId(id);
            conversations.deleteById(id);
            redis.opsForZSet().remove(DEADLINES, value);
        }
    }
}
