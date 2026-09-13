package org.collabmind.realtime.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class RealtimeEventPublisher {
    private final KafkaTemplate<String, Object> kafka;
    private final StringRedisTemplate redis;
    private final String messageTopic;
    private final String aiTopic;

    public RealtimeEventPublisher(KafkaTemplate<String, Object> kafka, StringRedisTemplate redis,
            @Value("${collabmind.kafka.message-created-topic}") String messageTopic,
            @Value("${collabmind.kafka.ai-requested-topic}") String aiTopic) {
        this.kafka = kafka; this.redis = redis; this.messageTopic = messageTopic; this.aiTopic = aiTopic;
    }

    public void messageCreated(String commandId, org.collabmind.realtime.chatcore.client.ChatCoreMessageResponse message) {
        var event = new MessageCreatedEvent(UUID.randomUUID().toString(), commandId, message, Instant.now());
        sendDurably(messageTopic, message.conversationId().toString(), event);
    }

    public void aiRequested(String jwt, String commandId, UUID userId,
                            org.collabmind.realtime.chatcore.client.ChatCoreMessageResponse message, String agentType) {
        String eventId = UUID.randomUUID().toString();
        redis.opsForValue().set("ai:auth:" + eventId, jwt, Duration.ofMinutes(10));
        var event = new AiRequestedEvent(eventId, commandId, userId, message, agentType, Instant.now());
        sendDurably(aiTopic, message.conversationId().toString(), event);
    }

    private void sendDurably(String topic, String key, Object event) {
        try {
            kafka.send(topic, key, event).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to durably publish event to " + topic, exception);
        }
    }
}
