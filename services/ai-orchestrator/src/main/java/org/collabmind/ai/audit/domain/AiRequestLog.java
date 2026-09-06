package org.collabmind.ai.audit.domain;

import jakarta.persistence.*;
import org.collabmind.ai.agent.domain.AgentType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_request_logs")
public class AiRequestLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false)
    private AgentType agentType;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiRequestLog() {
    }

    private AiRequestLog(
            UUID conversationId,
            UUID userId,
            AgentType agentType,
            String providerName,
            long latencyMs,
            boolean success,
            String errorMessage
    ) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.agentType = agentType;
        this.providerName = providerName;
        this.latencyMs = latencyMs;
        this.success = success;
        this.errorMessage = errorMessage;
        this.createdAt = Instant.now();
    }

    public static AiRequestLog success(
            UUID conversationId,
            UUID userId,
            AgentType agentType,
            String providerName,
            long latencyMs
    ) {
        return new AiRequestLog(
                conversationId,
                userId,
                agentType,
                providerName,
                latencyMs,
                true,
                null
        );
    }

    public static AiRequestLog failure(
            UUID conversationId,
            UUID userId,
            AgentType agentType,
            String providerName,
            long latencyMs,
            String errorMessage
    ) {
        return new AiRequestLog(
                conversationId,
                userId,
                agentType,
                providerName,
                latencyMs,
                false,
                errorMessage
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public AgentType getAgentType() {
        return agentType;
    }

    public String getProviderName() {
        return providerName;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
