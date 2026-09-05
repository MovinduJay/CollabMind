package org.collabmind.realtime.websocket.application;

import org.collabmind.realtime.chatcore.client.ChatCoreClient;
import org.collabmind.realtime.chatcore.client.ChatCoreMembershipResponse;
import org.collabmind.realtime.websocket.protocol.ClientCommand;
import org.collabmind.realtime.websocket.protocol.ServerEvent;
import org.collabmind.realtime.websocket.session.ConnectedClient;
import org.collabmind.realtime.websocket.session.ConversationSubscriptionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ConversationSubscriptionService {

    private final ChatCoreClient chatCoreClient;
    private final ConversationSubscriptionRegistry subscriptionRegistry;
    private final RealtimeFanoutService fanoutService;

    public ConversationSubscriptionService(
            ChatCoreClient chatCoreClient,
            ConversationSubscriptionRegistry subscriptionRegistry,
            RealtimeFanoutService fanoutService
    ) {
        this.chatCoreClient = chatCoreClient;
        this.subscriptionRegistry = subscriptionRegistry;
        this.fanoutService = fanoutService;
    }

    public void subscribe(
            ConnectedClient client,
            ClientCommand command
    ) {
        String conversationIdValue = command.conversationId();

        if (conversationIdValue == null || conversationIdValue.isBlank()) {
            sendSubscriptionRejected(client, command, "Missing conversationId");
            return;
        }

        try {
            UUID conversationId = UUID.fromString(conversationIdValue);

            ChatCoreMembershipResponse membership = chatCoreClient.checkMembership(
                    conversationId,
                    client.userId()
            );

            if (!membership.member()) {
                sendSubscriptionRejected(
                        client,
                        command,
                        "User is not a member of this conversation"
                );
                return;
            }

            subscriptionRegistry.subscribe(conversationId.toString(), client.sessionId());

            ServerEvent subscribedEvent = ServerEvent.of(
                    "SUBSCRIBED_CONVERSATION",
                    conversationId.toString(),
                    Map.of(
                            "commandId", command.commandId(),
                            "conversationId", conversationId.toString(),
                            "subscriberCount", subscriptionRegistry.subscriberCount(conversationId.toString())
                    )
            );

            fanoutService.sendToClient(client, subscribedEvent);

            ServerEvent joinedEvent = ServerEvent.of(
                    "USER_JOINED_CONVERSATION",
                    conversationId.toString(),
                    Map.of(
                            "conversationId", conversationId.toString(),
                            "userId", client.userId().toString(),
                            "sessionId", client.sessionId(),
                            "subscriberCount", subscriptionRegistry.subscriberCount(conversationId.toString())
                    )
            );

            fanoutService.sendToConversationExcept(
                    conversationId.toString(),
                    client.sessionId(),
                    joinedEvent
            );

        } catch (IllegalArgumentException exception) {
            sendSubscriptionRejected(client, command, "Invalid conversationId");
        } catch (RestClientResponseException exception) {
            sendSubscriptionRejected(
                    client,
                    command,
                    "chat-core rejected membership check with status " + exception.getStatusCode().value()
            );
        }
    }

    public void unsubscribe(
            ConnectedClient client,
            ClientCommand command
    ) {
        String conversationIdValue = command.conversationId();

        if (conversationIdValue == null || conversationIdValue.isBlank()) {
            sendSubscriptionRejected(client, command, "Missing conversationId");
            return;
        }

        subscriptionRegistry.unsubscribe(conversationIdValue, client.sessionId());

        ServerEvent unsubscribedEvent = ServerEvent.of(
                "UNSUBSCRIBED_CONVERSATION",
                conversationIdValue,
                Map.of(
                        "commandId", command.commandId(),
                        "conversationId", conversationIdValue
                )
        );

        fanoutService.sendToClient(client, unsubscribedEvent);

        ServerEvent leftEvent = ServerEvent.of(
                "USER_LEFT_CONVERSATION",
                conversationIdValue,
                Map.of(
                        "conversationId", conversationIdValue,
                        "userId", client.userId().toString(),
                        "sessionId", client.sessionId(),
                        "reason", "unsubscribed"
                )
        );

        fanoutService.sendToConversation(conversationIdValue, leftEvent);
    }

    private void sendSubscriptionRejected(
            ConnectedClient client,
            ClientCommand command,
            String reason
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", command.commandId());
        payload.put("reason", reason);

        ServerEvent rejectedEvent = ServerEvent.of(
                "SUBSCRIPTION_REJECTED",
                command.conversationId(),
                payload
        );

        fanoutService.sendToClient(client, rejectedEvent);
    }
}
