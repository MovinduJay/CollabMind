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

    public void subscribe(ConnectedClient client, ClientCommand command) {
        try {
            String conversationId = command.conversationId();

            if (conversationId == null || conversationId.isBlank()) {
                sendRejected(client, command, null, "conversationId is required");
                return;
            }

            UUID parsedConversationId = UUID.fromString(conversationId);

            ChatCoreMembershipResponse membership = chatCoreClient.checkMembership(
                    parsedConversationId,
                    client.userId(),
                    client.jwtToken()
            );

            if (membership == null || !membership.member()) {
                sendRejected(client, command, conversationId, "User is not a member of this conversation");
                return;
            }

            subscriptionRegistry.subscribe(conversationId, client.sessionId());

            Map<String, Object> payload = new HashMap<>();
            payload.put("commandId", command.commandId());
            payload.put("conversationId", conversationId);
            payload.put("subscriberCount", subscriptionRegistry.subscriberCount(conversationId));

            fanoutService.sendToClient(
                    client,
                    ServerEvent.of("SUBSCRIBED_CONVERSATION", conversationId, payload)
            );

            Map<String, Object> joinedPayload = new HashMap<>();
            joinedPayload.put("userId", client.userId().toString());
            joinedPayload.put("subscriberCount", subscriptionRegistry.subscriberCount(conversationId));

            fanoutService.sendToConversationExcept(
                    conversationId,
                    client.sessionId(),
                    ServerEvent.of("USER_JOINED_CONVERSATION", conversationId, joinedPayload)
            );

        } catch (RestClientResponseException exception) {
            sendRejected(
                    client,
                    command,
                    command.conversationId(),
                    "chat-core rejected membership check with status " + exception.getStatusCode().value()
            );
        } catch (Exception exception) {
            sendRejected(
                    client,
                    command,
                    command.conversationId(),
                    "Unable to subscribe to conversation"
            );
        }
    }

    public void unsubscribe(ConnectedClient client, ClientCommand command) {
        String conversationId = command.conversationId();

        if (conversationId == null || conversationId.isBlank()) {
            return;
        }

        subscriptionRegistry.unsubscribe(conversationId, client.sessionId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", command.commandId());
        payload.put("conversationId", conversationId);

        fanoutService.sendToClient(
                client,
                ServerEvent.of("UNSUBSCRIBED_CONVERSATION", conversationId, payload)
        );

        Map<String, Object> leftPayload = new HashMap<>();
        leftPayload.put("userId", client.userId().toString());
        leftPayload.put("subscriberCount", subscriptionRegistry.subscriberCount(conversationId));

        fanoutService.sendToConversationExcept(
                conversationId,
                client.sessionId(),
                ServerEvent.of("USER_LEFT_CONVERSATION", conversationId, leftPayload)
        );
    }

    private void sendRejected(
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
                ServerEvent.of("SUBSCRIPTION_REJECTED", conversationId, payload)
        );
    }
}

