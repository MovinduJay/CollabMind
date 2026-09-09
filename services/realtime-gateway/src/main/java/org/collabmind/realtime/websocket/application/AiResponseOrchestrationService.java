package org.collabmind.realtime.websocket.application;

import org.collabmind.realtime.ai.client.AiContextMessage;
import org.collabmind.realtime.ai.client.AiOrchestratorClient;
import org.collabmind.realtime.ai.client.AiPromptRequest;
import org.collabmind.realtime.ai.client.AiPromptResponse;
import org.collabmind.realtime.chatcore.client.ChatCoreClient;
import org.collabmind.realtime.chatcore.client.ChatCoreMessageResponse;
import org.collabmind.realtime.chatcore.client.ChatCoreSaveAiMessageRequest;
import org.collabmind.realtime.websocket.protocol.ServerEvent;
import org.collabmind.realtime.websocket.session.ConnectedClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AiResponseOrchestrationService {

    private static final int CONTEXT_MESSAGE_LIMIT = 10;
    private static final int MAX_CONTEXT_MESSAGE_CONTENT_LENGTH = 500;
    private static final int MAX_AI_MESSAGE_CONTENT_LENGTH = 11_800;

    private static final String USER_MESSAGE_TYPE = "USER";

    private final AiOrchestratorClient aiOrchestratorClient;
    private final ChatCoreClient chatCoreClient;
    private final RealtimeFanoutService fanoutService;
    private final AiRequestGuard aiRequestGuard;

    public AiResponseOrchestrationService(
            AiOrchestratorClient aiOrchestratorClient,
            ChatCoreClient chatCoreClient,
            RealtimeFanoutService fanoutService,
            AiRequestGuard aiRequestGuard
    ) {
        this.aiOrchestratorClient = aiOrchestratorClient;
        this.chatCoreClient = chatCoreClient;
        this.fanoutService = fanoutService;
        this.aiRequestGuard = aiRequestGuard;
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<Void> generateAndPersistAiResponse(
            ConnectedClient client,
            String commandId,
            ChatCoreMessageResponse savedUserMessage,
            String agentType
    ) {
        Instant startedAt = Instant.now();
        String stage = "STARTING_AI_FLOW";
        AiRequestGuard.Decision guardDecision = null;

        try {
            guardDecision = aiRequestGuard.tryBegin(
                    client.userId(),
                    savedUserMessage.conversationId(),
                    savedUserMessage.id(),
                    agentType
            );

            if (!guardDecision.accepted()) {
                sendAiGuardRejected(
                        client,
                        commandId,
                        savedUserMessage.conversationId().toString(),
                        savedUserMessage.id().toString(),
                        agentType,
                        guardDecision
                );

                return CompletableFuture.completedFuture(null);
            }

            publishStageUpdate(
                    client,
                    commandId,
                    savedUserMessage.conversationId().toString(),
                    agentType,
                    stage,
                    startedAt,
                    "AI response flow accepted and started"
            );

            stage = "FETCHING_CONTEXT";

            publishStageUpdate(
                    client,
                    commandId,
                    savedUserMessage.conversationId().toString(),
                    agentType,
                    stage,
                    startedAt,
                    "Fetching recent user-only context from chat-core"
            );

            List<AiContextMessage> contextMessages = fetchRecentUserContext(
                    client,
                    savedUserMessage
            );

            stage = "CALLING_AI_ORCHESTRATOR";

            publishStageUpdate(
                    client,
                    commandId,
                    savedUserMessage.conversationId().toString(),
                    agentType,
                    stage,
                    startedAt,
                    "Calling ai-orchestrator provider strategy"
            );

            AiPromptRequest aiRequest = new AiPromptRequest(
                    savedUserMessage.conversationId(),
                    client.userId(),
                    agentType,
                    savedUserMessage.content(),
                    contextMessages
            );

            AiPromptResponse aiResponse = aiOrchestratorClient.generateResponse(aiRequest);

            stage = "SAVING_AI_MESSAGE";

            publishStageUpdate(
                    client,
                    commandId,
                    savedUserMessage.conversationId().toString(),
                    agentType,
                    stage,
                    startedAt,
                    "Persisting AI response in chat-core"
            );

            UUID aiClientMessageId = createDeterministicAiClientMessageId(
                    savedUserMessage.id(),
                    agentType
            );

            String safeAiContent = truncateForChatCore(aiResponse.response());

            ChatCoreSaveAiMessageRequest saveAiRequest = new ChatCoreSaveAiMessageRequest(
                    aiClientMessageId,
                    savedUserMessage.id(),
                    aiResponse.agentType(),
                    safeAiContent
            );

            ChatCoreMessageResponse savedAiMessage = chatCoreClient.saveAiMessage(
                    savedUserMessage.conversationId(),
                    client.jwtToken(),
                    saveAiRequest
            );

            long totalElapsedMs = elapsedMs(startedAt);

            Map<String, Object> payload = new HashMap<>();
            payload.put("commandId", commandId);
            payload.put("sourceMessageId", savedUserMessage.id().toString());
            payload.put("contextMessageCount", contextMessages.size());
            payload.put("providerName", aiResponse.providerName());
            payload.put("primaryProviderName", aiResponse.primaryProviderName());
            payload.put("fallbackUsed", aiResponse.fallbackUsed());
            payload.put("providerLatencyMs", aiResponse.latencyMs());
            payload.put("totalElapsedMs", totalElapsedMs);
            payload.put("finalStage", "AI_MESSAGE_SAVED");
            payload.put("message", savedAiMessage);

            ServerEvent aiMessageCreatedEvent = ServerEvent.of(
                    "AI_MESSAGE_CREATED",
                    savedAiMessage.conversationId().toString(),
                    payload
            );

            fanoutService.sendToClient(
                    client,
                    aiMessageCreatedEvent
            );

            fanoutService.sendToConversationExcept(
                    savedAiMessage.conversationId().toString(),
                    client.sessionId(),
                    aiMessageCreatedEvent
            );

        } catch (RestClientResponseException exception) {
            sendAiFailed(
                    client,
                    commandId,
                    savedUserMessage.conversationId().toString(),
                    agentType,
                    stage,
                    startedAt,
                    "AI flow failed at stage [" + stage + "] with downstream status "
                            + exception.getStatusCode().value()
                            + ". Body: "
                            + exception.getResponseBodyAsString()
            );
        } catch (Exception exception) {
            sendAiFailed(
                    client,
                    commandId,
                    savedUserMessage.conversationId().toString(),
                    agentType,
                    stage,
                    startedAt,
                    "Unexpected error at stage [" + stage + "]: " + exception.getMessage()
            );
        } finally {
            if (guardDecision != null && guardDecision.accepted()) {
                aiRequestGuard.complete(guardDecision.requestKey());
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    private List<AiContextMessage> fetchRecentUserContext(
            ConnectedClient client,
            ChatCoreMessageResponse savedUserMessage
    ) {
        long afterSequence = Math.max(
                0,
                savedUserMessage.sequenceNumber() - CONTEXT_MESSAGE_LIMIT
        );

        return chatCoreClient.findMessagesAfter(
                        savedUserMessage.conversationId(),
                        afterSequence,
                        CONTEXT_MESSAGE_LIMIT,
                        client.jwtToken()
                )
                .stream()
                .filter(message -> USER_MESSAGE_TYPE.equalsIgnoreCase(message.messageType()))
                .map(message -> new AiContextMessage(
                        message.sequenceNumber(),
                        message.messageType(),
                        truncateContextContent(message.content()),
                        message.agentType()
                ))
                .toList();
    }

    private void publishStageUpdate(
            ConnectedClient client,
            String commandId,
            String conversationId,
            String agentType,
            String stage,
            Instant startedAt,
            String detail
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", commandId);
        payload.put("agentType", agentType);
        payload.put("stage", stage);
        payload.put("detail", detail);
        payload.put("elapsedMs", elapsedMs(startedAt));

        ServerEvent event = ServerEvent.of(
                "AI_STAGE_UPDATED",
                conversationId,
                payload
        );

        fanoutService.sendToClient(client, event);

        fanoutService.sendToConversationExcept(
                conversationId,
                client.sessionId(),
                event
        );
    }

    private String truncateContextContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        if (content.length() <= MAX_CONTEXT_MESSAGE_CONTENT_LENGTH) {
            return content;
        }

        return content.substring(0, MAX_CONTEXT_MESSAGE_CONTENT_LENGTH)
                + "... [context truncated]";
    }

    private UUID createDeterministicAiClientMessageId(
            UUID sourceMessageId,
            String agentType
    ) {
        String idempotencyKey = "AI_RESPONSE:" + sourceMessageId + ":" + agentType;

        return UUID.nameUUIDFromBytes(
                idempotencyKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String truncateForChatCore(String content) {
        String fallbackMessage = "AI response was empty.";

        if (content == null || content.isBlank()) {
            return fallbackMessage;
        }

        if (content.length() <= MAX_AI_MESSAGE_CONTENT_LENGTH) {
            return content;
        }

        return content.substring(0, MAX_AI_MESSAGE_CONTENT_LENGTH)
                + "\n\n[AI response was truncated because it exceeded the chat message limit.]";
    }

    private void sendAiGuardRejected(
            ConnectedClient client,
            String commandId,
            String conversationId,
            String sourceMessageId,
            String agentType,
            AiRequestGuard.Decision decision
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", commandId);
        payload.put("sourceMessageId", sourceMessageId);
        payload.put("agentType", agentType);
        payload.put("reason", decision.reason());
        payload.put("requestKey", decision.requestKey());
        payload.put("retryAfterMs", decision.retryAfterMs());

        fanoutService.sendToClient(
                client,
                ServerEvent.of(
                        decision.eventType(),
                        conversationId,
                        payload
                )
        );
    }

    private void sendAiFailed(
            ConnectedClient client,
            String commandId,
            String conversationId,
            String agentType,
            String failedStage,
            Instant startedAt,
            String reason
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", commandId);
        payload.put("agentType", agentType);
        payload.put("failedStage", failedStage);
        payload.put("elapsedMs", elapsedMs(startedAt));
        payload.put("reason", reason);

        fanoutService.sendToClient(
                client,
                ServerEvent.of(
                        "AI_RESPONSE_FAILED",
                        conversationId,
                        payload
                )
        );
    }

    private long elapsedMs(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }
}
