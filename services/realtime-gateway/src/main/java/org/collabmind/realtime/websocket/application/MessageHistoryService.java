package org.collabmind.realtime.websocket.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.collabmind.realtime.chatcore.client.ChatCoreClient;
import org.collabmind.realtime.chatcore.client.ChatCoreMembershipResponse;
import org.collabmind.realtime.chatcore.client.ChatCoreMessageResponse;
import org.collabmind.realtime.websocket.protocol.ClientCommand;
import org.collabmind.realtime.websocket.protocol.FetchMessagesPayload;
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

    public void fetchMessagesAfter(
            ConnectedClient client,
            ClientCommand command
    ) {
        String conversationIdValue = command.conversationId();

        if (conversationIdValue == null || conversationIdValue.isBlank()) {
            sendHistoryFailed(client, command, "Missing conversationId");
            return;
        }

        try {
            UUID conversationId = UUID.fromString(conversationIdValue);

            ChatCoreMembershipResponse membership = chatCoreClient.checkMembership(
                    conversationId,
                    client.userId()
            );

            if (!membership.member()) {
                sendHistoryFailed(
                        client,
                        command,
                        "User is not a member of this conversation"
                );
                return;
            }

            FetchMessagesPayload payload = objectMapper.convertValue(
                    command.payload(),
                    FetchMessagesPayload.class
            );

            long afterSequence = payload.afterSequence() == null
                    ? 0L
                    : Math.max(0L, payload.afterSequence());

            int limit = payload.limit() == null
                    ? DEFAULT_LIMIT
                    : Math.max(1, Math.min(payload.limit(), MAX_LIMIT));

            List<ChatCoreMessageResponse> messages = chatCoreClient.findMessagesAfter(
                    conversationId,
                    afterSequence,
                    limit
            );

            ServerEvent historyEvent = ServerEvent.of(
                    "MESSAGE_HISTORY",
                    conversationId.toString(),
                    Map.of(
                            "commandId", command.commandId(),
                            "conversationId", conversationId.toString(),
                            "afterSequence", afterSequence,
                            "limit", limit,
                            "count", messages.size(),
                            "messages", messages
                    )
            );

            fanoutService.sendToClient(client, historyEvent);

        } catch (IllegalArgumentException exception) {
            sendHistoryFailed(client, command, "Invalid conversationId or payload");
        } catch (RestClientResponseException exception) {
            sendHistoryFailed(
                    client,
                    command,
                    "chat-core rejected history request with status " + exception.getStatusCode().value()
            );
        } catch (Exception exception) {
            sendHistoryFailed(
                    client,
                    command,
                    "Unexpected error while fetching message history"
            );
        }
    }

    private void sendHistoryFailed(
            ConnectedClient client,
            ClientCommand command,
            String reason
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", command.commandId());
        payload.put("reason", reason);

        ServerEvent failedEvent = ServerEvent.of(
                "MESSAGE_HISTORY_FAILED",
                command.conversationId(),
                payload
        );

        fanoutService.sendToClient(client, failedEvent);
    }
}
