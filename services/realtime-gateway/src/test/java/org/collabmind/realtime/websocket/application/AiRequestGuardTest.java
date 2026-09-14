package org.collabmind.realtime.websocket.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRequestGuardTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private AiRequestGuard guard;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.hasKey(anyString())).thenReturn(false);
        guard = new AiRequestGuard(redis, 3, 300);
    }

    @Test
    void acceptsTheFirstRequestAndNormalizesTheAgentKey() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        AiRequestGuard.Decision decision = guard.tryBegin(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"), "kapruka");

        assertThat(decision.accepted()).isTrue();
        assertThat(decision.requestKey()).endsWith(":KAPRUKA");
    }

    @Test
    void rejectsRateLimitedRequestsAndReleasesTheInflightLock() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true, false);
        when(redis.getExpire(anyString())).thenReturn(2L);

        AiRequestGuard.Decision decision = guard.tryBegin(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "AI");

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.eventType()).isEqualTo("AI_RATE_LIMITED");
        assertThat(decision.retryAfterMs()).isEqualTo(2_000);
        verify(redis).delete(decision.requestKey());
    }
}
