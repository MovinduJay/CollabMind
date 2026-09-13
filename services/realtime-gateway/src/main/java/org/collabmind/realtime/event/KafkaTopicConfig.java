package org.collabmind.realtime.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    @Bean NewTopic messageCreatedTopic(@Value("${collabmind.kafka.message-created-topic}") String name) {
        return TopicBuilder.name(name).partitions(6).replicas(1).config("retention.ms", "604800000").build();
    }
    @Bean NewTopic aiRequestedTopic(@Value("${collabmind.kafka.ai-requested-topic}") String name) {
        return TopicBuilder.name(name).partitions(6).replicas(1).config("retention.ms", "604800000").build();
    }
    @Bean NewTopic aiDeadLetterTopic(@Value("${collabmind.kafka.ai-dlt-topic}") String name) {
        return TopicBuilder.name(name).partitions(6).replicas(1).config("retention.ms", "2592000000").build();
    }
}
