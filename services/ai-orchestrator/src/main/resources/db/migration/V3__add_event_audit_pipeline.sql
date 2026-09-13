CREATE TABLE IF NOT EXISTS event_audit_records (
    id UUID PRIMARY KEY,
    topic_name VARCHAR(255) NOT NULL,
    partition_number INTEGER NOT NULL,
    record_offset BIGINT NOT NULL,
    event_key VARCHAR(255),
    payload TEXT NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_event_audit_offset UNIQUE (topic_name, partition_number, record_offset)
);
CREATE INDEX IF NOT EXISTS idx_event_audit_received_at ON event_audit_records(received_at DESC);
