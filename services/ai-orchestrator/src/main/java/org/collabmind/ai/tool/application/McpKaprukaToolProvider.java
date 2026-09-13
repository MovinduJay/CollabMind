package org.collabmind.ai.tool.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class McpKaprukaToolProvider implements AiToolProvider {

    private static final String TOOL_NAME = "kapruka.search";
    private final RestClient restClient;
    private final String bridgeBaseUrl;

    public McpKaprukaToolProvider(
            RestClient.Builder restClientBuilder,
            @Value("${collabmind.tools.mcp-kapruka.base-url:http://localhost:8085}") String bridgeBaseUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.bridgeBaseUrl = bridgeBaseUrl;
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public boolean supports(String toolName) {
        return TOOL_NAME.equalsIgnoreCase(toolName);
    }

    @Override
    public ToolCallResponse invoke(ToolCallRequest request) {
        Instant startedAt = Instant.now();
        try {
            JsonNode response = restClient.post()
                    .uri(bridgeBaseUrl + "/mcp")
                    .body(Map.of(
                            "jsonrpc", "2.0",
                            "id", UUID.randomUUID().toString(),
                            "method", "tools/call",
                            "params", Map.of(
                                    "name", TOOL_NAME,
                                    "arguments", Map.of(
                                            "conversationId", request.conversationId().toString(),
                                            "userId", request.userId().toString(),
                                            "userMessage", safe(request.userMessage()),
                                            "contextSummary", safe(request.contextSummary())
                                    )
                            )
                    ))
                    .retrieve()
                    .body(JsonNode.class);

            long latency = elapsed(startedAt);
            if (response == null) return ToolCallResponse.failed(TOOL_NAME, "MCP bridge returned an empty response", latency);
            if (response.has("error")) return ToolCallResponse.failed(TOOL_NAME, response.path("error").path("message").asText("Unknown MCP error"), latency);
            String text = response.path("result").path("content").path(0).path("text").asText("").trim();
            return text.isBlank()
                    ? ToolCallResponse.failed(TOOL_NAME, "MCP bridge returned no product data", latency)
                    : ToolCallResponse.success(TOOL_NAME, text, latency);
        } catch (Exception exception) {
            return ToolCallResponse.failed(TOOL_NAME, "Kapruka tool call failed: " + exception.getMessage(), elapsed(startedAt));
        }
    }

    private long elapsed(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
