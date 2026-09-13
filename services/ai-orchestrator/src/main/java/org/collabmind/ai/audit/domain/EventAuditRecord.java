package org.collabmind.ai.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_audit_records", uniqueConstraints = @UniqueConstraint(name = "uk_event_audit_offset", columnNames = {"topic_name", "partition_number", "record_offset"}))
public class EventAuditRecord {
    @Id private UUID id;
    @Column(name="topic_name", nullable=false) private String topic;
    @Column(name="partition_number", nullable=false) private int partitionNumber;
    @Column(name="record_offset", nullable=false) private long offset;
    @Column(name="event_key") private String eventKey;
    @Column(nullable=false, columnDefinition="TEXT") private String payload;
    @Column(name="received_at", nullable=false) private Instant receivedAt;
    protected EventAuditRecord() {}
    public EventAuditRecord(String topic, int partition, long offset, String eventKey, String payload) {
        this.id = UUID.nameUUIDFromBytes((topic + ":" + partition + ":" + offset).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.topic=topic; this.partitionNumber=partition; this.offset=offset; this.eventKey=eventKey; this.payload=payload; this.receivedAt=Instant.now();
    }
    public UUID getId(){return id;} public String getTopic(){return topic;} public int getPartitionNumber(){return partitionNumber;}
    public long getOffset(){return offset;} public String getEventKey(){return eventKey;} public String getPayload(){return payload;} public Instant getReceivedAt(){return receivedAt;}
}
