package org.collabmind.realtime.websocket.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.collabmind.realtime.chatcore.client.ChatCoreClient;
import org.collabmind.realtime.chatcore.client.ChatCoreMessageResponse;
import org.collabmind.realtime.websocket.protocol.ClientCommand;
import org.collabmind.realtime.websocket.protocol.ServerEvent;
import org.collabmind.realtime.websocket.session.ConnectedClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MessageHistoryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final ObjectMapper objectMapper;
    private final ChatCoreClient chatCoreClient;
    private final RealtimeFanoutService fanoutService;

    public MessageHistoryService(
            ObjectMapper objectMapper,
            ChatCoreClient chatCoreClient,
            RealtimeFanoutService fanoutService
    ) {
        this.objectMapper = objectMapper;
        this.chatCoreClient = chatCoreClient;
        this.fanoutService = fanoutService;
    }

    public void fetchMessagesAfter(ConnectedClient client, ClientCommand command) {
        try {
            if (command.conversationId() == null || command.conversationId().isBlank()) {
                sendHistoryFailed(client, command, null, "conversationId is required");
                return;
            }

            FetchMessagesPayload payload = command.payload() == null
                    ? new FetchMessagesPayload(0, DEFAULT_LIMIT)
                    : objectMapper.treeToValue(command.payload(), FetchMessagesPayload.class);

            long afterSequence = payload == null ? 0 : payload.afterSequence();
            int requestedLimit = payload == null ? DEFAULT_LIMIT : payload.limit();
            int safeLimit = Math.max(1, Math.min(requestedLimit, MAX_LIMIT));

            UUID conversationId = UUID.fromString(command.conversationId());

            List<ChatCoreMessageResponse> messages = chatCoreClient.findMessagesAfter(
                    conversationId,
                    afterSequence,
                    safeLimit,
                    client.jwtToken()
            );

            Map<String, Object> responsePayload = new HashMap<>();
            responsePayload.put("commandId", command.commandId());
            responsePayload.put("afterSequence", afterSequence);
            responsePayload.put("limit", safeLimit);
            responsePayload.put("messages", messages);

            fanoutService.sendToClient(
                    client,
                    ServerEvent.of(
                            "MESSAGE_HISTORY",
                            command.conversationId(),
                            responsePayload
                    )
            );

        } catch (RestClientResponseException exception) {
            sendHistoryFailed(
                    client,
                    command,
                    command.conversationId(),
                    "chat-core rejected history request with status " + exception.getStatusCode().value()
            );
        } catch (Exception exception) {
            sendHistoryFailed(
                    client,
                    command,
                    command.conversationId(),
                    "Unable to fetch message history"
            );
        }
    }

    private void sendHistoryFailed(
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
                        "MESSAGE_HISTORY_FAILED",
                        conversationId,
                        payload
                )
        );
    }

    private record FetchMessagesPayload(
            long afterSequence,
            int limit
    ) {
    }
}
