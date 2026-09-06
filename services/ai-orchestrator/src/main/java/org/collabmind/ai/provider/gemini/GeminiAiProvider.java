package org.collabmind.ai.provider.gemini;

import org.collabmind.ai.agent.domain.AgentType;
import org.collabmind.ai.agent.web.AiPromptRequest;
import org.collabmind.ai.provider.application.AiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GeminiAiProvider implements AiProvider {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiAiProvider(
            RestClient.Builder restClientBuilder,
            @Value("${collabmind.ai.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${collabmind.ai.gemini.api-key:}") String apiKey,
            @Value("${collabmind.ai.gemini.model:gemini-2.0-flash}") String model
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();

        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    public String generateResponse(
            AiPromptRequest request,
            String contextSummary
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        String prompt = buildPrompt(request, contextSummary);

        GeminiGenerateContentRequest geminiRequest =
                GeminiGenerateContentRequest.fromText(prompt);

        GeminiGenerateContentResponse response = restClient.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(geminiRequest)
                .retrieve()
                .body(GeminiGenerateContentResponse.class);

        if (response == null) {
            throw new IllegalStateException("Gemini returned empty response body");
        }

        return response.firstText();
    }

    private String buildPrompt(
            AiPromptRequest request,
            String contextSummary
    ) {
        return """
                You are an AI collaboration agent inside CollabMind.
                
                Agent type: %s
                
                Agent behavior:
                %s
                
                Conversation context:
                %s
                
                Latest user message:
                %s
                
                Give a helpful, structured, concise response for the team.
                """.formatted(
                request.agentType(),
                agentInstruction(request.agentType()),
                contextSummary,
                request.message()
        );
    }

    private String agentInstruction(AgentType agentType) {
        return switch (agentType) {
            case PLANNER -> "Break the discussion into practical tasks, priorities, and next steps.";
            case CRITIC -> "Challenge assumptions, identify risks, and suggest what to validate.";
            case SUMMARIZER -> "Summarize the discussion into decisions, key points, and action items.";
            case RESEARCHER -> "Suggest research directions, comparisons, evidence to collect, and validation steps.";
        };
    }
}
