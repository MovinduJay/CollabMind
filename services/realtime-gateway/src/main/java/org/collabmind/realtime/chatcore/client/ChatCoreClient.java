package org.collabmind.realtime.chatcore.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class ChatCoreClient {

    private final RestClient restClient;

    public ChatCoreClient(
            RestClient.Builder restClientBuilder,
            @Value("${collabmind.chat-core.base-url}") String chatCoreBaseUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(chatCoreBaseUrl)
                .build();
    }

    public ChatCoreMessageResponse sendMessage(
            UUID conversationId,
            ChatCoreSendMessageRequest request
    ) {
        return restClient.post()
                .uri("/api/conversations/{conversationId}/messages", conversationId)
                .body(request)
                .retrieve()
                .body(ChatCoreMessageResponse.class);
    }

    public ChatCoreMembershipResponse checkMembership(
            UUID conversationId,
            UUID userId
    ) {
        return restClient.get()
                .uri("/api/conversations/{conversationId}/members/{userId}/exists", conversationId, userId)
                .retrieve()
                .body(ChatCoreMembershipResponse.class);
    }

    public ChatCoreMessageResponse saveAiMessage(
            UUID conversationId,
            ChatCoreSaveAiMessageRequest request
    ) {
        return restClient.post()
                .uri("/api/conversations/{conversationId}/messages/ai", conversationId)
                .body(request)
                .retrieve()
                .body(ChatCoreMessageResponse.class);
    }

    public List<ChatCoreMessageResponse> findMessagesAfter(
            UUID conversationId,
            long afterSequence,
            int limit
    ) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/conversations/{conversationId}/messages")
                        .queryParam("afterSequence", afterSequence)
                        .queryParam("limit", limit)
                        .build(conversationId))
                .retrieve()
                .body(new ParameterizedTypeReference<List<ChatCoreMessageResponse>>() {
                });
    }
}
