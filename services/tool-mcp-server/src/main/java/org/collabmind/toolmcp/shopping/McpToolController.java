package org.collabmind.toolmcp.shopping;

import com.fasterxml.jackson.databind.JsonNode;
import org.collabmind.toolmcp.github.GitHubToolService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mcp")
public class McpToolController {

    private final ShoppingSearchService shoppingSearchService;
    private final GitHubToolService gitHubToolService;

    public McpToolController(
            ShoppingSearchService shoppingSearchService,
            GitHubToolService gitHubToolService
    ) {
        this.shoppingSearchService = shoppingSearchService;
        this.gitHubToolService = gitHubToolService;
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
                List.of(
                        shoppingToolDefinition(),
                        githubRepoSummaryToolDefinition(),
                        githubSearchIssuesToolDefinition()
                )
        );
    }

    private Map<String, Object> toolsListResponse(String id) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "result", Map.of(
                        "tools",
                        List.of(
                                shoppingToolDefinition(),
                                githubRepoSummaryToolDefinition(),
                                githubSearchIssuesToolDefinition()
                        )
                )
        );
    }

    private Map<String, Object> toolsCallResponse(
            String id,
            JsonNode params
    ) {
        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");

        String userMessage = arguments.path("userMessage").asText("");
        String contextSummary = arguments.path("contextSummary").asText("");

        String result = switch (toolName) {
            case "shopping.search" -> shoppingSearchService.search(userMessage, contextSummary);
            case "github.repo_summary" -> gitHubToolService.repoSummary(userMessage, contextSummary);
            case "github.search_issues" -> gitHubToolService.searchIssues(userMessage, contextSummary);
            default -> null;
        };

        if (result == null) {
            return errorResponse(id, -32602, "Unsupported tool: " + toolName);
        }

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

    private Map<String, Object> githubRepoSummaryToolDefinition() {
        return Map.of(
                "name", "github.repo_summary",
                "description", "Summarizes a public GitHub repository using the GitHub API.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "userMessage", Map.of(
                                        "type", "string",
                                        "description", "Message containing a repository slug such as owner/repo."
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

    private Map<String, Object> githubSearchIssuesToolDefinition() {
        return Map.of(
                "name", "github.search_issues",
                "description", "Searches public GitHub issues for a repository.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "userMessage", Map.of(
                                        "type", "string",
                                        "description", "Message containing a repository slug and issue search query."
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
