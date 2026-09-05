package org.collabmind.realtime.ai.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiOrchestratorClient {

    private final RestClient restClient;

    public AiOrchestratorClient(
            RestClient.Builder restClientBuilder,
            @Value("${collabmind.ai-orchestrator.base-url}") String aiOrchestratorBaseUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(aiOrchestratorBaseUrl)
                .build();
    }

    public AiPromptResponse generateResponse(AiPromptRequest request) {
        return restClient.post()
                .uri("/api/ai/respond")
                .body(request)
                .retrieve()
                .body(AiPromptResponse.class);
    }
}
