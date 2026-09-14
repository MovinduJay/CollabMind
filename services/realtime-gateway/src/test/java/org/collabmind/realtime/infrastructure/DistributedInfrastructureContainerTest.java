package org.collabmind.realtime.infrastructure;

import io.lettuce.core.RedisClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class DistributedInfrastructureContainerTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Test
    void redisSupportsTheAtomicTtlGuardUsedForAiDeduplication() {
        RedisClient client = RedisClient.create("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        try (var connection = client.connect()) {
            assertThat(connection.sync().setnx("ai:inflight:test", "1")).isTrue();
            assertThat(connection.sync().expire("ai:inflight:test", 30)).isTrue();
            assertThat(connection.sync().setnx("ai:inflight:test", "1")).isFalse();
            assertThat(connection.sync().ttl("ai:inflight:test")).isPositive();
        } finally {
            client.shutdown();
        }
    }

    @Test
    void kafkaDurablyTransfersARealtimeEvent() throws Exception {
        String topic = "collabmind.test.message-created";
        Map<String, Object> producerProperties = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all");
        Map<String, Object> consumerProperties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "collabmind-integration-test",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties);
             KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties)) {
            consumer.subscribe(List.of(topic));
            producer.send(new ProducerRecord<>(topic, "room-1", "{\"type\":\"MESSAGE_CREATED\"}")).get();

            var records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records).anySatisfy(record -> {
                assertThat(record.key()).isEqualTo("room-1");
                assertThat(record.value()).contains("MESSAGE_CREATED");
            });
        }
    }
}
