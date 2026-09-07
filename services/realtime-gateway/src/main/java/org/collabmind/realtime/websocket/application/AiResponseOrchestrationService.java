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

            ChatCoreSaveAiMessageRequest saveAiRequest = new ChatCoreSaveAiMessageRequest(
                    aiClientMessageId,
                    savedUserMessage.id(),
                    aiResponse.agentType(),
                    aiResponse.response()
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

            fanoutService.sendToConversation(
                    savedAiMessage.conversationId().toString(),
                    ServerEvent.of(
                            "AI_MESSAGE_CREATED",
                            savedAiMessage.conversationId().toString(),
                            payload
                    )
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
                .map(message -> new AiContextMessage(
                        message.sequenceNumber(),
                        message.messageType(),
                        message.content(),
                        message.agentType()
                ))
                .toList();
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
