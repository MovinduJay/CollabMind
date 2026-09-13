package org.collabmind.realtime.common.config;

import org.collabmind.realtime.websocket.application.RealtimeFanoutService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisRealtimeConfig {
    @Bean
    RedisMessageListenerContainer realtimeListenerContainer(
            RedisConnectionFactory connectionFactory,
            RealtimeFanoutService fanoutService,
            @Value("${collabmind.redis.realtime-channel:collabmind:realtime}") String channel
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(fanoutService, new ChannelTopic(channel));
        return container;
    }
}
