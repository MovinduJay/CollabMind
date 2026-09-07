package org.collabmind.realtime.websocket.application;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AiRequestGuard {

    private static final long COOLDOWN_MS = 3_000;
    private static final long COOLDOWN_ENTRY_TTL_MS = 300_000;

    private final ConcurrentSkipListSet<String> inFlightRequestKeys = new ConcurrentSkipListSet<>();
    private final ConcurrentMap<String, Instant> lastAcceptedAtByCooldownKey = new ConcurrentHashMap<>();

    public Decision tryBegin(
            UUID userId,
            UUID conversationId,
            UUID sourceMessageId,
            String agentType
    ) {
        cleanupOldCooldownEntries();

        String requestKey = requestKey(conversationId, sourceMessageId, agentType);

        if (!inFlightRequestKeys.add(requestKey)) {
            return Decision.rejected(
                    "AI_REQUEST_DUPLICATE_IGNORED",
                    "Duplicate AI request ignored because the same source message and agent are already being processed.",
                    requestKey,
                    0
            );
        }

        String cooldownKey = cooldownKey(userId, conversationId, agentType);
        Instant now = Instant.now();
        AtomicReference<Instant> previousAcceptedAtRef = new AtomicReference<>();

        lastAcceptedAtByCooldownKey.compute(
                cooldownKey,
                (key, previousAcceptedAt) -> {
                    previousAcceptedAtRef.set(previousAcceptedAt);

                    if (previousAcceptedAt == null) {
                        return now;
                    }

                    long elapsedMs = Duration.between(previousAcceptedAt, now).toMillis();

                    if (elapsedMs >= COOLDOWN_MS) {
                        return now;
                    }

                    return previousAcceptedAt;
                }
        );

        Instant previousAcceptedAt = previousAcceptedAtRef.get();

        if (previousAcceptedAt != null) {
            long elapsedMs = Duration.between(previousAcceptedAt, now).toMillis();

            if (elapsedMs < COOLDOWN_MS) {
                inFlightRequestKeys.remove(requestKey);

                long retryAfterMs = Math.max(0, COOLDOWN_MS - elapsedMs);

                return Decision.rejected(
                        "AI_RATE_LIMITED",
                        "AI request rate limited to protect the system from repeated expensive LLM calls.",
                        requestKey,
                        retryAfterMs
                );
            }
        }

        return Decision.accepted(requestKey);
    }

    public void complete(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            return;
        }

        inFlightRequestKeys.remove(requestKey);
    }

    private String requestKey(
            UUID conversationId,
            UUID sourceMessageId,
            String agentType
    ) {
        return conversationId + ":" + sourceMessageId + ":" + normalizeAgent(agentType);
    }

    private String cooldownKey(
            UUID userId,
            UUID conversationId,
            String agentType
    ) {
        return userId + ":" + conversationId + ":" + normalizeAgent(agentType);
    }

    private String normalizeAgent(String agentType) {
        if (agentType == null || agentType.isBlank()) {
            return "UNKNOWN";
        }

        return agentType.trim().toUpperCase();
    }

    private void cleanupOldCooldownEntries() {
        Instant cutoff = Instant.now().minusMillis(COOLDOWN_ENTRY_TTL_MS);

        Iterator<Map.Entry<String, Instant>> iterator = lastAcceptedAtByCooldownKey.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();

            if (entry.getValue().isBefore(cutoff)) {
                iterator.remove();
            }
        }
    }

    public record Decision(
            boolean accepted,
            String eventType,
            String reason,
            String requestKey,
            long retryAfterMs
    ) {
        public static Decision accepted(String requestKey) {
            return new Decision(
                    true,
                    null,
                    null,
                    requestKey,
                    0
            );
        }

        public static Decision rejected(
                String eventType,
                String reason,
                String requestKey,
                long retryAfterMs
        ) {
            return new Decision(
                    false,
                    eventType,
                    reason,
                    requestKey,
                    retryAfterMs
            );
        }
    }
}
