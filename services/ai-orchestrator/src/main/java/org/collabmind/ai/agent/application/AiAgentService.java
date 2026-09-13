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

    private final AiProvider primaryProvider;
    private final AiProvider fallbackProvider;
    private final boolean fallbackEnabled;
    private final AiRequestLogRepository auditLogRepository;
    private final List<AgentContextEnricher> contextEnrichers;

    public AiAgentService(
            List<AiProvider> aiProviders,
            List<AgentContextEnricher> contextEnrichers,
            AiRequestLogRepository auditLogRepository,
            @Value("${collabmind.ai.provider:mock}") String selectedProviderName,
            @Value("${collabmind.ai.fallback-provider:mock}") String fallbackProviderName,
            @Value("${collabmind.ai.fallback-enabled:true}") boolean fallbackEnabled
    ) {
        this.primaryProvider = resolveProvider(aiProviders, selectedProviderName);
        this.fallbackProvider = resolveProvider(aiProviders, fallbackProviderName);
        this.fallbackEnabled = fallbackEnabled;
        this.auditLogRepository = auditLogRepository;
        this.contextEnrichers = contextEnrichers;
    }

    public AiPromptResponse generateResponse(AiPromptRequest request) {
        List<AiContextMessage> contextMessages = request.contextMessages() == null
                ? List.of()
                : request.contextMessages();

        String contextSummary = enrichContext(request, buildContextSummary(contextMessages));
        long overallStartedAtNanos = System.nanoTime();

        try {
            ProviderCallResult primaryResult = callProvider(
                    primaryProvider,
                    request,
                    contextSummary
            );

            auditLogRepository.save(AiRequestLog.success(
                    request.conversationId(),
                    request.userId(),
                    request.agentType(),
                    primaryProvider.providerName(),
                    primaryResult.latencyMs()
            ));

            return new AiPromptResponse(
                    request.conversationId(),
                    request.userId(),
                    request.agentType(),
                    primaryProvider.providerName(),
                    primaryProvider.providerName(),
                    false,
                    calculateLatencyMs(overallStartedAtNanos),
                    primaryResult.response(),
                    Instant.now()
            );

        } catch (RuntimeException primaryException) {
            long primaryLatencyMs = calculateLatencyMs(overallStartedAtNanos);

            auditLogRepository.save(AiRequestLog.failure(
                    request.conversationId(),
                    request.userId(),
                    request.agentType(),
                    primaryProvider.providerName(),
                    primaryLatencyMs,
                    safeErrorMessage(primaryException)
            ));

            if (!shouldUseFallback()) {
                throw primaryException;
            }

            long fallbackStartedAtNanos = System.nanoTime();

            try {
                ProviderCallResult fallbackResult = callProvider(
                        fallbackProvider,
                        request,
                        contextSummary
                );

                auditLogRepository.save(AiRequestLog.success(
                        request.conversationId(),
                        request.userId(),
                        request.agentType(),
                        fallbackProvider.providerName(),
                        fallbackResult.latencyMs()
                ));

                return new AiPromptResponse(
                        request.conversationId(),
                        request.userId(),
                        request.agentType(),
                        fallbackProvider.providerName(),
                        primaryProvider.providerName(),
                        true,
                        calculateLatencyMs(overallStartedAtNanos),
                        fallbackResult.response(),
                        Instant.now()
                );

            } catch (RuntimeException fallbackException) {
                long fallbackLatencyMs = calculateLatencyMs(fallbackStartedAtNanos);

                auditLogRepository.save(AiRequestLog.failure(
                        request.conversationId(),
                        request.userId(),
                        request.agentType(),
                        fallbackProvider.providerName(),
                        fallbackLatencyMs,
                        safeErrorMessage(fallbackException)
                ));

                fallbackException.addSuppressed(primaryException);
                throw fallbackException;
            }
        }
    }

    private AiProvider resolveProvider(
            List<AiProvider> aiProviders,
            String providerName
    ) {
        return aiProviders.stream()
                .filter(provider -> provider.providerName().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No AI provider configured with name: " + providerName
                ));
    }

    private boolean shouldUseFallback() {
        return fallbackEnabled &&
                !primaryProvider.providerName().equalsIgnoreCase(fallbackProvider.providerName());
    }

    private ProviderCallResult callProvider(
            AiProvider provider,
            AiPromptRequest request,
            String contextSummary
    ) {
        long startedAtNanos = System.nanoTime();

        String response = provider.generateResponse(
                request,
                contextSummary
        );

        return new ProviderCallResult(
                response,
                calculateLatencyMs(startedAtNanos)
        );
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

    private String enrichContext(AiPromptRequest request, String contextSummary) {
        String enriched = contextSummary;
        for (AgentContextEnricher enricher : contextEnrichers) {
            if (enricher.supports(request.agentType())) {
                enriched = enricher.enrich(request, enriched);
            }
        }
        return enriched;
    }

    private record ProviderCallResult(
            String response,
            long latencyMs
    ) {
    }
}
