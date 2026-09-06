package org.collabmind.realtime.websocket.application;

import org.collabmind.realtime.websocket.protocol.ClientCommand;
import org.collabmind.realtime.websocket.protocol.ServerEvent;
import org.collabmind.realtime.websocket.session.ConnectedClient;
import org.collabmind.realtime.websocket.session.ConversationSubscriptionRegistry;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class TypingIndicatorService {

    private final ConversationSubscriptionRegistry subscriptionRegistry;
    private final RealtimeFanoutService fanoutService;

    public TypingIndicatorService(
            ConversationSubscriptionRegistry subscriptionRegistry,
            RealtimeFanoutService fanoutService
    ) {
        this.subscriptionRegistry = subscriptionRegistry;
        this.fanoutService = fanoutService;
    }

    public void startTyping(
            ConnectedClient client,
            ClientCommand command
    ) {
        sendTypingEvent(client, command, "USER_TYPING");
    }

    public void stopTyping(
            ConnectedClient client,
            ClientCommand command
    ) {
        sendTypingEvent(client, command, "USER_STOPPED_TYPING");
    }

    private void sendTypingEvent(
            ConnectedClient client,
            ClientCommand command,
            String eventType
    ) {
        String conversationId = command.conversationId();

        if (conversationId == null || conversationId.isBlank()) {
            sendTypingFailed(client, command, "Missing conversationId");
            return;
        }

        if (!subscriptionRegistry.isSubscribed(conversationId, client.sessionId())) {
            sendTypingFailed(client, command, "Client is not subscribed to this conversation");
            return;
        }

        ServerEvent typingEvent = ServerEvent.of(
                eventType,
                conversationId,
                Map.of(
                        "commandId", command.commandId(),
                        "conversationId", conversationId,
                        "userId", client.userId().toString()
                )
        );

        fanoutService.sendToConversationExcept(
                conversationId,
                client.sessionId(),
                typingEvent
        );
    }

    private void sendTypingFailed(
            ConnectedClient client,
            ClientCommand command,
            String reason
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", command.commandId());
        payload.put("reason", reason);

        ServerEvent failedEvent = ServerEvent.of(
                "TYPING_EVENT_FAILED",
                command.conversationId(),
                payload
        );

        fanoutService.sendToClient(client, failedEvent);
    }
}
