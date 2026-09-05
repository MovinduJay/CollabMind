package org.collabmind.realtime.websocket.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.collabmind.realtime.ai.client.AiOrchestratorClient;
import org.collabmind.realtime.ai.client.AiPromptRequest;
import org.collabmind.realtime.ai.client.AiPromptResponse;
import org.collabmind.realtime.chatcore.client.ChatCoreClient;
import org.collabmind.realtime.chatcore.client.ChatCoreMessageResponse;
import org.collabmind.realtime.chatcore.client.ChatCoreSendMessageRequest;
import org.collabmind.realtime.websocket.protocol.ClientCommand;
import org.collabmind.realtime.websocket.protocol.SendMessagePayload;
import org.collabmind.realtime.websocket.protocol.ServerEvent;
import org.collabmind.realtime.websocket.session.ConnectedClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

            ChatCoreMessageResponse savedMessage = chatCoreClient.sendMessage(
                    payload.conversationId(),
                    chatCoreRequest
            );

            ServerEvent messageCreatedEvent = ServerEvent.of(
                    "MESSAGE_CREATED",
                    savedMessage.conversationId().toString(),
                    Map.of(
                            "commandId", command.commandId(),
                            "message", savedMessage
                    )
            );

            fanoutService.sendToConversation(
                    savedMessage.conversationId().toString(),
                    messageCreatedEvent
            );

            triggerAiIfMentioned(client, command, savedMessage);

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
            ChatCoreMessageResponse savedMessage
    ) {
        Optional<String> agentType = aiMentionService.detectAgentType(savedMessage.content());

        if (agentType.isEmpty()) {
            return;
        }

        try {
            AiPromptRequest aiRequest = new AiPromptRequest(
                    savedMessage.conversationId(),
                    client.userId(),
                    agentType.get(),
                    savedMessage.content()
            );

            AiPromptResponse aiResponse = aiOrchestratorClient.generateResponse(aiRequest);

            ServerEvent aiResponseEvent = ServerEvent.of(
                    "AI_RESPONSE_CREATED",
                    savedMessage.conversationId().toString(),
                    Map.of(
                            "commandId", command.commandId(),
                            "sourceMessageId", savedMessage.id().toString(),
                            "agentType", aiResponse.agentType(),
                            "response", aiResponse.response(),
                            "createdAt", aiResponse.createdAt().toString()
                    )
            );

            fanoutService.sendToConversation(
                    savedMessage.conversationId().toString(),
                    aiResponseEvent
            );

        } catch (RestClientResponseException exception) {
            sendAiFailed(
                    client,
                    command,
                    "ai-orchestrator rejected request with status " + exception.getStatusCode().value()
            );
        } catch (Exception exception) {
            sendAiFailed(
                    client,
                    command,
                    "Unexpected error while generating AI response"
            );
        }
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
