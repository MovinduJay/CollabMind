package org.collabmind.toolmcp.audit;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class ToolKafkaConfig {
    @Bean NewTopic toolInvokedTopic(@Value("${collabmind.kafka.tool-invoked-topic}") String name) {
        return TopicBuilder.name(name).partitions(6).replicas(1).config("retention.ms", "2592000000").build();
    }
}
