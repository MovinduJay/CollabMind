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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AiResponseOrchestrationService {

    private static final int CONTEXT_MESSAGE_LIMIT = 10;
    private static final int MAX_AI_MESSAGE_CONTENT_LENGTH = 11_800;

    private final AiOrchestratorClient aiOrchestratorClient;
    private final ChatCoreClient chatCoreClient;
    private final RealtimeFanoutService fanoutService;

    public AiResponseOrchestrationService(
            AiOrchestratorClient aiOrchestratorClient,
            ChatCoreClient chatCoreClient,
            RealtimeFanoutService fanoutService
    ) {
        this.aiOrchestratorClient = aiOrchestratorClient;
        this.chatCoreClient = chatCoreClient;
        this.fanoutService = fanoutService;
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<Void> generateAndPersistAiResponse(
            ConnectedClient client,
            String commandId,
            ChatCoreMessageResponse savedUserMessage,
            String agentType
    ) {
        String stage = "starting_ai_flow";

        try {
            stage = "fetching_recent_context_from_chat_core";

            List<AiContextMessage> contextMessages = fetchRecentContext(
                    client,
                    savedUserMessage
            );

            stage = "calling_ai_orchestrator";

            AiPromptRequest aiRequest = new AiPromptRequest(
                    savedUserMessage.conversationId(),
                    client.userId(),
                    agentType,
                    savedUserMessage.content(),
                    contextMessages
            );

            AiPromptResponse aiResponse = aiOrchestratorClient.generateResponse(aiRequest);

            stage = "saving_ai_message_to_chat_core";

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

            Map<String, Object> payload = new HashMap<>();
            payload.put("commandId", commandId);
            payload.put("sourceMessageId", savedUserMessage.id().toString());
            payload.put("contextMessageCount", contextMessages.size());
            payload.put("providerName", aiResponse.providerName());
            payload.put("primaryProviderName", aiResponse.primaryProviderName());
            payload.put("fallbackUsed", aiResponse.fallbackUsed());
            payload.put("latencyMs", aiResponse.latencyMs());
            payload.put("message", savedAiMessage);

            ServerEvent aiMessageCreatedEvent = ServerEvent.of(
                    "AI_MESSAGE_CREATED",
                    savedAiMessage.conversationId().toString(),
                    payload
            );

// Send directly to the user who triggered the AI.
// This confirms the AI flow works even if room subscription fan-out has a bug.
            fanoutService.sendToClient(
                    client,
                    aiMessageCreatedEvent
            );

// Also try room fan-out for other subscribed users.
// If subscription registry is fixed later, other users will receive it too.
            fanoutService.sendToConversation(
                    savedAiMessage.conversationId().toString(),
                    aiMessageCreatedEvent
            );

        } catch (RestClientResponseException exception) {
            sendAiFailed(
                    client,
                    commandId,
                    savedUserMessage.conversationId().toString(),
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
                    "Unexpected error at stage [" + stage + "]: " + exception.getMessage()
            );
        }

        return CompletableFuture.completedFuture(null);
    }

    private List<AiContextMessage> fetchRecentContext(
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
                .filter(message -> "USER".equalsIgnoreCase(message.messageType()))
                .map(message -> new AiContextMessage(
                        message.sequenceNumber(),
                        message.messageType(),
                        truncateContextContent(message.content()),
                        message.agentType()
                ))
                .toList();
    }

    private String truncateContextContent(String content) {
        int maxContextMessageLength = 500;

        if (content == null || content.isBlank()) {
            return "";
        }

        if (content.length() <= maxContextMessageLength) {
            return content;
        }

        return content.substring(0, maxContextMessageLength)
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

    private void sendAiFailed(
            ConnectedClient client,
            String commandId,
            String conversationId,
            String reason
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", commandId);
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
}
