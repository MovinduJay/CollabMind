package org.collabmind.realtime.chatcore.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
}
