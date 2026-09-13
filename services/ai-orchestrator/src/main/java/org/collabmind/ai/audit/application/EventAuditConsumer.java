package org.collabmind.ai.audit.application;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.collabmind.ai.audit.domain.EventAuditRecord;
import org.collabmind.ai.audit.infrastructure.EventAuditRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EventAuditConsumer {
    private final EventAuditRepository repository;
    public EventAuditConsumer(EventAuditRepository repository) { this.repository = repository; }

    @KafkaListener(topics = {"${collabmind.kafka.message-created-topic}", "${collabmind.kafka.ai-requested-topic}", "${collabmind.kafka.tool-invoked-topic}"}, groupId = "event-audit-v1")
    public void record(ConsumerRecord<String, String> record) {
        repository.save(new EventAuditRecord(record.topic(), record.partition(), record.offset(), record.key(), record.value()));
    }
}
