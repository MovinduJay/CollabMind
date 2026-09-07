package org.collabmind.realtime.websocket.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.collabmind.realtime.chatcore.client.ChatCoreClient;
import org.collabmind.realtime.chatcore.client.ChatCoreMessageResponse;
import org.collabmind.realtime.chatcore.client.ChatCoreSendMessageRequest;
import org.collabmind.realtime.websocket.protocol.ClientCommand;
import org.collabmind.realtime.websocket.protocol.ServerEvent;
import org.collabmind.realtime.websocket.session.ConnectedClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class MessageRelayService {

    private final ObjectMapper objectMapper;
    private final ChatCoreClient chatCoreClient;
    private final RealtimeFanoutService fanoutService;
    private final AiMentionService aiMentionService;
    private final AiResponseOrchestrationService aiResponseOrchestrationService;

    public MessageRelayService(
            ObjectMapper objectMapper,
            ChatCoreClient chatCoreClient,
            RealtimeFanoutService fanoutService,
            AiMentionService aiMentionService,
            AiResponseOrchestrationService aiResponseOrchestrationService
    ) {
        this.objectMapper = objectMapper;
        this.chatCoreClient = chatCoreClient;
        this.fanoutService = fanoutService;
        this.aiMentionService = aiMentionService;
        this.aiResponseOrchestrationService = aiResponseOrchestrationService;
    }

    public void relaySendMessage(ConnectedClient client, ClientCommand command) {
        handleSendMessage(client, command);
    }

    public void relayMessage(ConnectedClient client, ClientCommand command) {
        handleSendMessage(client, command);
    }

    public void sendMessage(ConnectedClient client, ClientCommand command) {
        handleSendMessage(client, command);
    }

    public void handleSendMessage(ConnectedClient client, ClientCommand command) {
        try {
            if (command.conversationId() == null || command.conversationId().isBlank()) {
                sendMessageFailed(client, command, null, "conversationId is required");
                return;
            }

            SendMessagePayload payload = objectMapper.treeToValue(
                    command.payload(),
                    SendMessagePayload.class
            );

            if (payload == null || payload.clientMessageId() == null) {
                sendMessageFailed(client, command, command.conversationId(), "clientMessageId is required");
                return;
            }

            if (payload.content() == null || payload.content().isBlank()) {
                sendMessageFailed(client, command, command.conversationId(), "content is required");
                return;
            }

            UUID conversationId = UUID.fromString(command.conversationId());

            ChatCoreSendMessageRequest chatCoreRequest = new ChatCoreSendMessageRequest(
                    payload.clientMessageId(),
                    payload.content()
            );

            ChatCoreMessageResponse savedMessage = chatCoreClient.sendMessage(
                    conversationId,
                    client.jwtToken(),
                    chatCoreRequest
            );

            Map<String, Object> messagePayload = new HashMap<>();
            messagePayload.put("commandId", command.commandId());
            messagePayload.put("message", savedMessage);

            fanoutService.sendToConversation(
                    savedMessage.conversationId().toString(),
                    ServerEvent.of(
                            "MESSAGE_CREATED",
                            savedMessage.conversationId().toString(),
                            messagePayload
                    )
            );

            Optional<String> agentType = aiMentionService.detectAgentType(savedMessage.content());

            agentType.ifPresent(detectedAgentType -> {
                Map<String, Object> thinkingPayload = new HashMap<>();
                thinkingPayload.put("commandId", command.commandId());
                thinkingPayload.put("sourceMessageId", savedMessage.id().toString());
                thinkingPayload.put("agentType", detectedAgentType);

                fanoutService.sendToConversation(
                        savedMessage.conversationId().toString(),
                        ServerEvent.of(
                                "AI_THINKING_STARTED",
                                savedMessage.conversationId().toString(),
                                thinkingPayload
                        )
                );

                aiResponseOrchestrationService.generateAndPersistAiResponse(
                        client,
                        command.commandId(),
                        savedMessage,
                        detectedAgentType
                );
            });

        } catch (RestClientResponseException exception) {
            sendMessageFailed(
                    client,
                    command,
                    command.conversationId(),
                    "chat-core rejected message with status " + exception.getStatusCode().value()
            );
        } catch (Exception exception) {
            sendMessageFailed(
                    client,
                    command,
                    command.conversationId(),
                    "Unable to send message"
            );
        }
    }

    private void sendMessageFailed(
            ConnectedClient client,
            ClientCommand command,
            String conversationId,
            String reason
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", command.commandId());
        payload.put("reason", reason);

        fanoutService.sendToClient(
                client,
                ServerEvent.of(
                        "MESSAGE_SEND_FAILED",
                        conversationId,
                        payload
                )
        );
    }

    private record SendMessagePayload(
            UUID clientMessageId,
            String content
    ) {
    }
}
