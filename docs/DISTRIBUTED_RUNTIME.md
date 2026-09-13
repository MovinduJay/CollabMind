# Distributed runtime

CollabMind uses Redis for ephemeral distributed coordination and Kafka for durable events.

## Redis keys

- `realtime:room:{conversationId}:sessions`: gateway-qualified WebSocket session IDs.
- `realtime:session:{instanceId}:{sessionId}`: user ID with a renewable presence TTL.
- `collabmind:realtime`: Pub/Sub channel used to fan Kafka-consumed events to every gateway instance.
- `ai:inflight:{conversationId}:{messageId}:{agent}`: distributed AI idempotency marker.
- `ai:rate:{userId}:{conversationId}:{agent}`: distributed fixed-window AI rate limit.
- `ai:auth:{eventId}`: short-lived authorization material for an asynchronous AI job; never written to Kafka.
- `rooms:expiry-deadlines`: sorted set of room inactivity deadlines.
- `room:active:{conversationId}`: renewable room activity lease.

Gateway instances only retain their own live `WebSocketSession` objects. Subscription and presence metadata is shared in Redis. Kafka consumer groups deliver each durable event to one gateway, which publishes it through Redis so all gateway instances deliver to their local sockets.

## Kafka topics

- `collabmind.message-created.v1`: persisted chat messages, keyed by conversation ID.
- `collabmind.ai-requested.v1`: asynchronous AI jobs, keyed by conversation ID.
- `collabmind.ai-requested.v1.DLT`: jobs that exhaust retry processing.
- `collabmind.tool-invoked.v1`: successful and failed tool invocations.

Message and AI topics retain seven days. Tool and dead-letter topics retain thirty days. The AI orchestrator consumes all domain topics into `event_audit_records`, keyed idempotently by Kafka topic/partition/offset. A new consumer group can replay retained events from the beginning.

## Operations

Start infrastructure and services:

```powershell
docker compose up -d --build
```

Inspect recent audited events:

```text
GET http://localhost:8084/api/ai/event-audit/recent?limit=50
```

Production deployments should run Redis and Kafka as managed, authenticated, TLS-enabled services; increase Kafka replication factors to at least three; use separate service credentials; and export consumer lag, Redis memory, DLT depth, and expiry-cleaner metrics.
