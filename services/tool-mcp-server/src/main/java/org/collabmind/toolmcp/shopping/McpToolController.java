package org.collabmind.toolmcp.shopping;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mcp")
public class McpToolController {

    private final ShoppingSearchService shoppingSearchService;

    public McpToolController(ShoppingSearchService shoppingSearchService) {
        this.shoppingSearchService = shoppingSearchService;
    }

    @PostMapping
    public Map<String, Object> handle(@RequestBody JsonNode request) {
        String id = request.path("id").asText("1");
        String method = request.path("method").asText();

        return switch (method) {
            case "tools/list" -> toolsListResponse(id);
            case "tools/call" -> toolsCallResponse(id, request.path("params"));
            default -> errorResponse(id, -32601, "Method not found: " + method);
        };
    }

    @GetMapping("/tools")
    public Map<String, Object> tools() {
        return Map.of(
                "tools",
                List.of(shoppingToolDefinition())
        );
    }

    private Map<String, Object> toolsListResponse(String id) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "result", Map.of(
                        "tools", List.of(shoppingToolDefinition())
                )
        );
    }

    private Map<String, Object> toolsCallResponse(
            String id,
            JsonNode params
    ) {
        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");

        if (!"shopping.search".equalsIgnoreCase(toolName)) {
            return errorResponse(id, -32602, "Unsupported tool: " + toolName);
        }

        String userMessage = arguments.path("userMessage").asText("");
        String contextSummary = arguments.path("contextSummary").asText("");

        String result = shoppingSearchService.search(
                userMessage,
                contextSummary
        );

        return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "result", Map.of(
                        "content", List.of(
                                Map.of(
                                        "type", "text",
                                        "text", result
                                )
                        )
                )
        );
    }

    private Map<String, Object> shoppingToolDefinition() {
        return Map.of(
                "name", "shopping.search",
                "description", "Searches product-style shopping recommendations from a user request.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "userMessage", Map.of(
                                        "type", "string",
                                        "description", "The user's shopping request."
                                ),
                                "contextSummary", Map.of(
                                        "type", "string",
                                        "description", "Recent conversation context."
                                )
                        ),
                        "required", List.of("userMessage")
                )
        );
    }

    private Map<String, Object> errorResponse(
            String id,
            int code,
            String message
    ) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "error", Map.of(
                        "code", code,
                        "message", message
                )
        );
    }
}
