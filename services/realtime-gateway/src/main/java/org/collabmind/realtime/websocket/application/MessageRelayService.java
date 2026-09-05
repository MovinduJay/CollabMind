package org.collabmind.realtime.websocket.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.collabmind.realtime.ai.client.AiOrchestratorClient;
import org.collabmind.realtime.ai.client.AiPromptRequest;
import org.collabmind.realtime.ai.client.AiPromptResponse;
import org.collabmind.realtime.chatcore.client.ChatCoreClient;
import org.collabmind.realtime.chatcore.client.ChatCoreMessageResponse;
import org.collabmind.realtime.chatcore.client.ChatCoreSaveAiMessageRequest;
import org.collabmind.realtime.chatcore.client.ChatCoreSendMessageRequest;
import org.collabmind.realtime.websocket.protocol.ClientCommand;
import org.collabmind.realtime.websocket.protocol.SendMessagePayload;
import org.collabmind.realtime.websocket.protocol.ServerEvent;
import org.collabmind.realtime.websocket.session.ConnectedClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class MessageRelayService {

    private final ObjectMapper objectMapper;
    private final ChatCoreClient chatCoreClient;
    private final AiOrchestratorClient aiOrchestratorClient;
    private final AiMentionService aiMentionService;
    private final RealtimeFanoutService fanoutService;

    public MessageRelayService(
            ObjectMapper objectMapper,
            ChatCoreClient chatCoreClient,
            AiOrchestratorClient aiOrchestratorClient,
            AiMentionService aiMentionService,
            RealtimeFanoutService fanoutService
    ) {
        this.objectMapper = objectMapper;
        this.chatCoreClient = chatCoreClient;
        this.aiOrchestratorClient = aiOrchestratorClient;
        this.aiMentionService = aiMentionService;
        this.fanoutService = fanoutService;
    }

    public void relaySendMessage(
            ConnectedClient client,
            ClientCommand command
    ) {
        try {
            SendMessagePayload payload = objectMapper.convertValue(
                    command.payload(),
                    SendMessagePayload.class
            );

            if (payload.conversationId() == null) {
                sendMessageFailed(client, command, "Missing conversationId");
                return;
            }

            if (payload.clientMessageId() == null) {
                sendMessageFailed(client, command, "Missing clientMessageId");
                return;
            }

            if (payload.content() == null || payload.content().isBlank()) {
                sendMessageFailed(client, command, "Message content cannot be blank");
                return;
            }

            ChatCoreSendMessageRequest chatCoreRequest = new ChatCoreSendMessageRequest(
                    client.userId(),
                    payload.clientMessageId(),
                    payload.content()
            );

            ChatCoreMessageResponse savedUserMessage = chatCoreClient.sendMessage(
                    payload.conversationId(),
                    chatCoreRequest
            );

            ServerEvent userMessageCreatedEvent = ServerEvent.of(
                    "MESSAGE_CREATED",
                    savedUserMessage.conversationId().toString(),
                    Map.of(
                            "commandId", command.commandId(),
                            "message", savedUserMessage
                    )
            );

            fanoutService.sendToConversation(
                    savedUserMessage.conversationId().toString(),
                    userMessageCreatedEvent
            );

            triggerAiIfMentioned(client, command, savedUserMessage);

        } catch (IllegalArgumentException exception) {
            sendMessageFailed(client, command, "Invalid message payload");
        } catch (RestClientResponseException exception) {
            sendMessageFailed(
                    client,
                    command,
                    "chat-core rejected message with status " + exception.getStatusCode().value()
            );
        } catch (Exception exception) {
            sendMessageFailed(
                    client,
                    command,
                    "Unexpected error while sending message"
            );
        }
    }

    private void triggerAiIfMentioned(
            ConnectedClient client,
            ClientCommand command,
            ChatCoreMessageResponse savedUserMessage
    ) {
        Optional<String> agentType = aiMentionService.detectAgentType(savedUserMessage.content());

        if (agentType.isEmpty()) {
            return;
        }

        try {
            AiPromptRequest aiRequest = new AiPromptRequest(
                    savedUserMessage.conversationId(),
                    client.userId(),
                    agentType.get(),
                    savedUserMessage.content()
            );

            AiPromptResponse aiResponse = aiOrchestratorClient.generateResponse(aiRequest);

            UUID aiClientMessageId = createDeterministicAiClientMessageId(
                    savedUserMessage.id(),
                    agentType.get()
            );

            ChatCoreSaveAiMessageRequest saveAiRequest = new ChatCoreSaveAiMessageRequest(
                    client.userId(),
                    aiClientMessageId,
                    savedUserMessage.id(),
                    aiResponse.agentType(),
                    aiResponse.response()
            );

            ChatCoreMessageResponse savedAiMessage = chatCoreClient.saveAiMessage(
                    savedUserMessage.conversationId(),
                    saveAiRequest
            );

            ServerEvent aiMessageCreatedEvent = ServerEvent.of(
                    "AI_MESSAGE_CREATED",
                    savedAiMessage.conversationId().toString(),
                    Map.of(
                            "commandId", command.commandId(),
                            "sourceMessageId", savedUserMessage.id().toString(),
                            "message", savedAiMessage
                    )
            );

            fanoutService.sendToConversation(
                    savedAiMessage.conversationId().toString(),
                    aiMessageCreatedEvent
            );

        } catch (RestClientResponseException exception) {
            sendAiFailed(
                    client,
                    command,
                    "AI flow failed with downstream status " + exception.getStatusCode().value()
            );
        } catch (Exception exception) {
            sendAiFailed(
                    client,
                    command,
                    "Unexpected error while generating or saving AI response"
            );
        }
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

    private void sendMessageFailed(
            ConnectedClient client,
            ClientCommand command,
            String reason
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", command.commandId());
        payload.put("reason", reason);

        ServerEvent failedEvent = ServerEvent.of(
                "MESSAGE_SEND_FAILED",
                command.conversationId(),
                payload
        );

        fanoutService.sendToClient(client, failedEvent);
    }

    private void sendAiFailed(
            ConnectedClient client,
            ClientCommand command,
            String reason
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", command.commandId());
        payload.put("reason", reason);

        ServerEvent failedEvent = ServerEvent.of(
                "AI_RESPONSE_FAILED",
                command.conversationId(),
                payload
        );

        fanoutService.sendToClient(client, failedEvent);
    }
}
