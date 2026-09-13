package org.collabmind.realtime.websocket.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class AiRequestGuard {
    private final StringRedisTemplate redis;
    private final Duration cooldown;
    private final Duration requestTtl;

    public AiRequestGuard(StringRedisTemplate redis,
                          @Value("${collabmind.ai.rate-limit-seconds:3}") long cooldownSeconds,
                          @Value("${collabmind.ai.request-ttl-seconds:300}") long requestTtlSeconds) {
        this.redis = redis;
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
        this.requestTtl = Duration.ofSeconds(requestTtlSeconds);
    }

    public Decision tryBegin(UUID userId, UUID conversationId, UUID sourceMessageId, String agentType) {
        String requestKey = "ai:inflight:" + conversationId + ":" + sourceMessageId + ":" + normalize(agentType);
        String completedKey = requestKey.replace("ai:inflight:", "ai:completed:");
        if (Boolean.TRUE.equals(redis.hasKey(completedKey))) {
            return Decision.rejected("AI_REQUEST_DUPLICATE_IGNORED", "This AI request was already completed.", requestKey, 0);
        }
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(requestKey, "1", requestTtl))) {
            return Decision.rejected("AI_REQUEST_DUPLICATE_IGNORED", "This AI request is already being processed.", requestKey, 0);
        }

        String rateKey = "ai:rate:" + userId + ":" + conversationId + ":" + normalize(agentType);
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(rateKey, "1", cooldown))) {
            redis.delete(requestKey);
            Long ttl = redis.getExpire(rateKey);
            return Decision.rejected("AI_RATE_LIMITED", "Please wait before requesting another AI response.", requestKey, Math.max(0, ttl == null ? 0 : ttl * 1000));
        }
        return Decision.accepted(requestKey);
    }

    public void complete(String requestKey) {
        redis.delete(requestKey);
        redis.opsForValue().set(requestKey.replace("ai:inflight:", "ai:completed:"), "1", requestTtl);
    }

    public void fail(String requestKey) { redis.delete(requestKey); }

    private String normalize(String value) { return value == null ? "UNKNOWN" : value.trim().toUpperCase(); }

    public record Decision(boolean accepted, String eventType, String reason, String requestKey, long retryAfterMs) {
        public static Decision accepted(String key) { return new Decision(true, null, null, key, 0); }
        public static Decision rejected(String type, String reason, String key, long retry) { return new Decision(false, type, reason, key, retry); }
    }
}
