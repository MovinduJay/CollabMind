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

import java.util.Map;

@Service
public class MessageRelayService {

    private final ObjectMapper objectMapper;
    private final ChatCoreClient chatCoreClient;
    private final RealtimeFanoutService fanoutService;

    public MessageRelayService(
            ObjectMapper objectMapper,
            ChatCoreClient chatCoreClient,
            RealtimeFanoutService fanoutService
    ) {
        this.objectMapper = objectMapper;
        this.chatCoreClient = chatCoreClient;
        this.fanoutService = fanoutService;
    }

    public void relaySendMessage(
            ConnectedClient client,
            ClientCommand command
    ) {
        SendMessagePayload payload = objectMapper.convertValue(
                command.payload(),
                SendMessagePayload.class
        );

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
    }
}
