package org.collabmind.realtime.websocket.application;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final AiMentionService aiMentionService;
    private final AiResponseOrchestrationService aiResponseOrchestrationService;
    private final RealtimeFanoutService fanoutService;

    public MessageRelayService(
            ObjectMapper objectMapper,
            ChatCoreClient chatCoreClient,
            AiMentionService aiMentionService,
            AiResponseOrchestrationService aiResponseOrchestrationService,
            RealtimeFanoutService fanoutService
    ) {
        this.objectMapper = objectMapper;
        this.chatCoreClient = chatCoreClient;
        this.aiMentionService = aiMentionService;
        this.aiResponseOrchestrationService = aiResponseOrchestrationService;
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

            triggerAiInBackgroundIfMentioned(client, command, savedUserMessage);

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

    private void triggerAiInBackgroundIfMentioned(
            ConnectedClient client,
            ClientCommand command,
            ChatCoreMessageResponse savedUserMessage
    ) {
        Optional<String> agentType = aiMentionService.detectAgentType(savedUserMessage.content());

        if (agentType.isEmpty()) {
            return;
        }

        ServerEvent thinkingStartedEvent = ServerEvent.of(
                "AI_THINKING_STARTED",
                savedUserMessage.conversationId().toString(),
                Map.of(
                        "commandId", command.commandId(),
                        "sourceMessageId", savedUserMessage.id().toString(),
                        "agentType", agentType.get()
                )
        );

        fanoutService.sendToConversation(
                savedUserMessage.conversationId().toString(),
                thinkingStartedEvent
        );

        aiResponseOrchestrationService.generateAndPersistAiResponse(
                client,
                command.commandId(),
                savedUserMessage,
                agentType.get()
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
}
