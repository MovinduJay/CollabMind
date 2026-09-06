package org.collabmind.ai.agent.application;

import org.collabmind.ai.agent.web.AiContextMessage;
import org.collabmind.ai.agent.web.AiPromptRequest;
import org.collabmind.ai.agent.web.AiPromptResponse;
import org.collabmind.ai.audit.domain.AiRequestLog;
import org.collabmind.ai.audit.infrastructure.AiRequestLogRepository;
import org.collabmind.ai.provider.application.AiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AiAgentService {

    private final AiProvider aiProvider;
    private final AiRequestLogRepository auditLogRepository;

    public AiAgentService(
            List<AiProvider> aiProviders,
            AiRequestLogRepository auditLogRepository,
            @Value("${collabmind.ai.provider:mock}") String selectedProviderName
    ) {
        this.aiProvider = aiProviders.stream()
                .filter(provider -> provider.providerName().equalsIgnoreCase(selectedProviderName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No AI provider configured with name: " + selectedProviderName
                ));

        this.auditLogRepository = auditLogRepository;
    }

    public AiPromptResponse generateResponse(AiPromptRequest request) {
        List<AiContextMessage> contextMessages = request.contextMessages() == null
                ? List.of()
                : request.contextMessages();

        String contextSummary = buildContextSummary(contextMessages);

        long startedAtNanos = System.nanoTime();

        try {
            String response = aiProvider.generateResponse(
                    request,
                    contextSummary
            );

            long latencyMs = calculateLatencyMs(startedAtNanos);

            auditLogRepository.save(AiRequestLog.success(
                    request.conversationId(),
                    request.userId(),
                    request.agentType(),
                    aiProvider.providerName(),
                    latencyMs
            ));

            return new AiPromptResponse(
                    request.conversationId(),
                    request.userId(),
                    request.agentType(),
                    aiProvider.providerName(),
                    latencyMs,
                    response,
                    Instant.now()
            );

        } catch (RuntimeException exception) {
            long latencyMs = calculateLatencyMs(startedAtNanos);

            auditLogRepository.save(AiRequestLog.failure(
                    request.conversationId(),
                    request.userId(),
                    request.agentType(),
                    aiProvider.providerName(),
                    latencyMs,
                    safeErrorMessage(exception)
            ));

            throw exception;
        }
    }

    private long calculateLatencyMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private String safeErrorMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return exception.getMessage();
    }

    private String buildContextSummary(List<AiContextMessage> contextMessages) {
        if (contextMessages.isEmpty()) {
            return "No previous context was provided.";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Recent conversation context:\n");

        for (AiContextMessage message : contextMessages) {
            builder.append("- #")
                    .append(message.sequenceNumber())
                    .append(" [")
                    .append(message.messageType())
                    .append("] ");

            if (message.agentType() != null && !message.agentType().isBlank()) {
                builder.append("(").append(message.agentType()).append(") ");
            }

            builder.append(message.content())
                    .append("\n");
        }

        return builder.toString();
    }
}
