package org.collabmind.toolmcp.kapruka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class HttpKaprukaMcpClient implements KaprukaCatalogPort {

    private static final String MCP_SESSION_HEADER = "mcp-session-id";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;

    public HttpKaprukaMcpClient(
            ObjectMapper objectMapper,
            @Value("${collabmind.kapruka.mcp-url:https://mcp.kapruka.com/mcp}") URI endpoint,
            @Value("${collabmind.kapruka.timeout-seconds:15}") long timeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Override
    public String search(KaprukaSearchCriteria criteria) {
        try {
            String sessionId = initialize();
            notifyInitialized(sessionId);

            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("q", criteria.query());
            parameters.put("currency", criteria.currency());
            parameters.put("limit", criteria.limit());
            parameters.put("in_stock_only", true);
            parameters.put("sort", "relevance");
            parameters.put("response_format", "json");
            if (criteria.minPrice() != null) parameters.put("min_price", criteria.minPrice());
            if (criteria.maxPrice() != null) parameters.put("max_price", criteria.maxPrice());

            JsonNode response = send(Map.of(
                    "jsonrpc", "2.0",
                    "id", UUID.randomUUID().toString(),
                    "method", "tools/call",
                    "params", Map.of(
                            "name", "kapruka_search_products",
                            "arguments", Map.of("params", parameters)
                    )
            ), sessionId).body();

            JsonNode content = response.path("result").path("content").path(0);
            String text = content.path("text").asText("").trim();
            boolean toolError = response.path("result").path("isError").asBoolean(false);
            if (response.has("error") || toolError || text.isBlank()) {
                String reason = response.has("error")
                        ? response.path("error").path("message").asText("Unknown MCP error")
                        : text.isBlank() ? "Kapruka returned no product data" : text;
                throw new KaprukaMcpException(reason);
            }
            return text;
        } catch (KaprukaMcpException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new KaprukaMcpException("Kapruka MCP request failed", exception);
        }
    }

    private String initialize() throws Exception {
        McpResponse response = send(Map.of(
                "jsonrpc", "2.0",
                "id", UUID.randomUUID().toString(),
                "method", "initialize",
                "params", Map.of(
                        "protocolVersion", "2025-03-26",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "CollabMind", "version", "1.0.0")
                )
        ), null);
        return response.sessionId();
    }

    private void notifyInitialized(String sessionId) throws Exception {
        send(Map.of(
                "jsonrpc", "2.0",
                "method", "notifications/initialized",
                "params", Map.of()
        ), sessionId);
    }

    private McpResponse send(Map<String, Object> payload, String sessionId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
        if (sessionId != null) builder.header("Mcp-Session-Id", sessionId);

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new KaprukaMcpException("Kapruka MCP HTTP " + response.statusCode());
        }

        String responseSession = response.headers().firstValue(MCP_SESSION_HEADER).orElse(sessionId);
        if (responseSession == null || responseSession.isBlank()) {
            throw new KaprukaMcpException("Kapruka MCP did not return a session ID");
        }
        if (response.body() == null || response.body().isBlank()) {
            return new McpResponse(objectMapper.createObjectNode(), responseSession);
        }
        return new McpResponse(parseBody(response.body()), responseSession);
    }

    private JsonNode parseBody(String body) throws Exception {
        if (!body.stripLeading().startsWith("event:")) {
            return objectMapper.readTree(body);
        }
        for (String line : body.split("\\R")) {
            if (line.startsWith("data:")) {
                return objectMapper.readTree(line.substring(5).trim());
            }
        }
        throw new KaprukaMcpException("Kapruka MCP returned an invalid event stream");
    }

    private record McpResponse(JsonNode body, String sessionId) {
    }
}
