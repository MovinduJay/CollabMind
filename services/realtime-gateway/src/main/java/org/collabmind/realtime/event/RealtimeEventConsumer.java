package org.collabmind.realtime.event;

import org.collabmind.realtime.websocket.application.AiResponseOrchestrationService;
import org.collabmind.realtime.websocket.application.RealtimeFanoutService;
import org.collabmind.realtime.websocket.protocol.ServerEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RealtimeEventConsumer {
    private final RealtimeFanoutService fanout;
    private final AiResponseOrchestrationService ai;
    private final StringRedisTemplate redis;

    public RealtimeEventConsumer(RealtimeFanoutService fanout, AiResponseOrchestrationService ai, StringRedisTemplate redis) {
        this.fanout = fanout; this.ai = ai; this.redis = redis;
    }

    @KafkaListener(topics = "${collabmind.kafka.message-created-topic}", groupId = "realtime-message-fanout-v1")
    public void onMessageCreated(MessageCreatedEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", event.commandId());
        payload.put("eventId", event.eventId());
        payload.put("message", event.message());
        fanout.sendToConversation(event.message().conversationId().toString(),
                ServerEvent.of("MESSAGE_CREATED", event.message().conversationId().toString(), payload));
    }

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 1000, multiplier = 2.0), dltTopicSuffix = ".DLT")
    @KafkaListener(topics = "${collabmind.kafka.ai-requested-topic}", groupId = "ai-workers-v1")
    public void onAiRequested(AiRequestedEvent event) {
        String authKey = "ai:auth:" + event.eventId();
        String jwt = redis.opsForValue().get(authKey);
        if (jwt == null || jwt.isBlank()) {
            throw new IllegalStateException("AI job authorization expired before processing");
        }
        ai.generateAndPersistAiResponse(event.userId(), jwt, event.commandId(), event.sourceMessage(), event.agentType()).join();
        redis.delete(authKey);
    }
}
