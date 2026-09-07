package org.collabmind.realtime.chatcore.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Service
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

    public ChatCoreMembershipResponse checkMembership(
            UUID conversationId,
            UUID userId,
            String jwtToken
    ) {
        return restClient.get()
                .uri("/api/conversations/{conversationId}/members/{userId}/exists", conversationId, userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .retrieve()
                .body(ChatCoreMembershipResponse.class);
    }

    public ChatCoreMessageResponse sendMessage(
            UUID conversationId,
            String jwtToken,
            ChatCoreSendMessageRequest request
    ) {
        return restClient.post()
                .uri("/api/conversations/{conversationId}/messages", conversationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .body(request)
                .retrieve()
                .body(ChatCoreMessageResponse.class);
    }

    public ChatCoreMessageResponse saveAiMessage(
            UUID conversationId,
            String jwtToken,
            ChatCoreSaveAiMessageRequest request
    ) {
        return restClient.post()
                .uri("/api/conversations/{conversationId}/messages/ai", conversationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .body(request)
                .retrieve()
                .body(ChatCoreMessageResponse.class);
    }

    public List<ChatCoreMessageResponse> findMessagesAfter(
            UUID conversationId,
            long afterSequence,
            int limit,
            String jwtToken
    ) {
        List<ChatCoreMessageResponse> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/conversations/{conversationId}/messages")
                        .queryParam("afterSequence", afterSequence)
                        .queryParam("limit", limit)
                        .build(conversationId)
                )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return response == null ? List.of() : response;
    }
}
