package org.collabmind.ai.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import org.collabmind.ai.agent.domain.AgentType;
import org.collabmind.ai.agent.web.AiPromptRequest;
import org.collabmind.ai.provider.application.AiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class OpenAiProvider implements AiProvider {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiProvider(
            RestClient.Builder restClientBuilder,
            @Value("${collabmind.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${collabmind.ai.openai.api-key:}") String apiKey,
            @Value("${collabmind.ai.openai.model:gpt-5-nano}") String model
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public String generateResponse(AiPromptRequest request, String contextSummary) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is not configured");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "instructions", "You are an AI collaboration agent inside CollabMind. "
                        + agentInstruction(request.agentType())
                        + " Give a helpful, structured, concise response for the team.",
                "input", buildInput(request, contextSummary)
        );

        JsonNode response = restClient.post()
                .uri("/v1/responses")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String outputText = extractOutputText(response);
        if (outputText.isBlank()) {
            throw new IllegalStateException("OpenAI returned no text output");
        }

        return outputText;
    }

    private String buildInput(AiPromptRequest request, String contextSummary) {
        String context = contextSummary == null || contextSummary.isBlank()
                ? "No previous context."
                : contextSummary;

        return "Conversation context:\n" + context
                + "\n\nLatest user message:\n" + request.message();
    }

    private String extractOutputText(JsonNode response) {
        if (response == null) {
            return "";
        }

        JsonNode directOutputText = response.get("output_text");
        if (directOutputText != null && directOutputText.isTextual()) {
            return directOutputText.asText().trim();
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode outputItem : response.path("output")) {
            for (JsonNode contentItem : outputItem.path("content")) {
                if ("output_text".equals(contentItem.path("type").asText())) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(contentItem.path("text").asText());
                }
            }
        }
        return text.toString().trim();
    }

    private String agentInstruction(AgentType agentType) {
        if (agentType == null) {
            return "Help the team answer the latest request.";
        }

        return switch (agentType) {
            case PLANNER -> "Break the discussion into practical tasks, priorities, and next steps.";
            case CRITIC -> "Challenge assumptions, identify risks, and suggest what to validate.";
            case SUMMARIZER -> "Summarize the discussion into decisions, key points, and action items.";
            case RESEARCHER -> "Answer the user's request directly. Only discuss research methodology when the user explicitly asks for it.";
            case SHOPPING -> "Give direct, practical recommendations for products, restaurants, food, or places. Lead with useful options, not a research plan.";
            case GITHUB -> "Explain repository or issue information with engineering insights and next actions.";
            case KAPRUKA -> "Briefly introduce the best products in the supplied LIVE KAPRUKA MCP RESULTS. Never say products are unavailable when results exist. Do not repeat raw JSON. Never invent availability or promise delivery; say delivery must be checked for the selected product and city.";
        };
    }
}
