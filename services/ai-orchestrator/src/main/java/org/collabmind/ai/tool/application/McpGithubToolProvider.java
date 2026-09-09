package org.collabmind.ai.tool.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "collabmind.tools.github.provider",
        havingValue = "mcp",
        matchIfMissing = true
)
public class McpGithubToolProvider implements AiToolProvider {

    private final RestClient restClient;
    private final String mcpBaseUrl;

    public McpGithubToolProvider(
            RestClient.Builder restClientBuilder,
            @Value("${collabmind.tools.mcp-github.base-url:http://localhost:8085}") String mcpBaseUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.mcpBaseUrl = mcpBaseUrl;
    }

    @Override
    public String toolName() {
        return "github";
    }

    @Override
    public boolean supports(String toolName) {
        return toolName != null && toolName.toLowerCase().startsWith("github.");
    }

    @Override
    public ToolCallResponse invoke(ToolCallRequest request) {
        Instant startedAt = Instant.now();

        try {
            Map<String, Object> body = Map.of(
                    "jsonrpc", "2.0",
                    "id", UUID.randomUUID().toString(),
                    "method", "tools/call",
                    "params", Map.of(
                            "name", request.toolName(),
                            "arguments", Map.of(
                                    "conversationId", request.conversationId().toString(),
                                    "userId", request.userId().toString(),
                                    "userMessage", safe(request.userMessage()),
                                    "contextSummary", safe(request.contextSummary())
                            )
                    )
            );

            JsonNode response = restClient
                    .post()
                    .uri(mcpBaseUrl + "/mcp")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();

            if (response == null) {
                return ToolCallResponse.failed(
                        request.toolName(),
                        "MCP bridge returned empty response",
                        latencyMs
                );
            }

            if (response.has("error")) {
                return ToolCallResponse.failed(
                        request.toolName(),
                        response.path("error").path("message").asText("Unknown MCP error"),
                        latencyMs
                );
            }

            String text = response
                    .path("result")
                    .path("content")
                    .path(0)
                    .path("text")
                    .asText("");

            if (text.isBlank()) {
                return ToolCallResponse.failed(
                        request.toolName(),
                        "MCP bridge returned no text content",
                        latencyMs
                );
            }

            return ToolCallResponse.success(
                    request.toolName(),
                    text,
                    latencyMs
            );

        } catch (RestClientResponseException exception) {
            return ToolCallResponse.failed(
                    request.toolName(),
                    "MCP bridge HTTP error "
                            + exception.getStatusCode().value()
                            + ": "
                            + exception.getResponseBodyAsString(),
                    Duration.between(startedAt, Instant.now()).toMillis()
            );
        } catch (Exception exception) {
            return ToolCallResponse.failed(
                    request.toolName(),
                    "MCP bridge call failed: " + exception.getMessage(),
                    Duration.between(startedAt, Instant.now()).toMillis()
            );
        }
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }

        return value;
    }
}
