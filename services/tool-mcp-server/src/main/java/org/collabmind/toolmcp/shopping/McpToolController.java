package org.collabmind.toolmcp.shopping;

import com.fasterxml.jackson.databind.JsonNode;
import org.collabmind.toolmcp.audit.ToolAuditService;
import org.collabmind.toolmcp.github.GitHubToolService;
import org.collabmind.toolmcp.kapruka.KaprukaSearchService;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mcp")
public class McpToolController {

    private final ShoppingSearchService shoppingSearchService;
    private final GitHubToolService gitHubToolService;
    private final ToolAuditService toolAuditService;
    private final KaprukaSearchService kaprukaSearchService;

    public McpToolController(
            ShoppingSearchService shoppingSearchService,
            GitHubToolService gitHubToolService,
            ToolAuditService toolAuditService,
            KaprukaSearchService kaprukaSearchService
    ) {
        this.shoppingSearchService = shoppingSearchService;
        this.gitHubToolService = gitHubToolService;
        this.toolAuditService = toolAuditService;
        this.kaprukaSearchService = kaprukaSearchService;
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
                        kaprukaSearchToolDefinition(),
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
                                kaprukaSearchToolDefinition(),
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
        Instant startedAt = Instant.now();

        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");

        String userMessage = arguments.path("userMessage").asText("");
        String contextSummary = arguments.path("contextSummary").asText("");

        try {
            String result = switch (toolName) {
                case "shopping.search" -> shoppingSearchService.search(userMessage, contextSummary);
                case "kapruka.search" -> kaprukaSearchService.search(userMessage, contextSummary);
                case "github.repo_summary" -> gitHubToolService.repoSummary(userMessage, contextSummary);
                case "github.search_issues" -> gitHubToolService.searchIssues(userMessage, contextSummary);
                default -> null;
            };

            long latencyMs = elapsedMs(startedAt);

            if (result == null) {
                String errorMessage = "Unsupported tool: " + toolName;

                toolAuditService.recordFailure(
                        id,
                        "tools/call",
                        toolName,
                        errorMessage,
                        latencyMs
                );

                return errorResponse(id, -32602, errorMessage);
            }

            toolAuditService.recordSuccess(
                    id,
                    "tools/call",
                    toolName,
                    latencyMs
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

        } catch (Exception exception) {
            long latencyMs = elapsedMs(startedAt);

            toolAuditService.recordFailure(
                    id,
                    "tools/call",
                    toolName,
                    exception.getMessage(),
                    latencyMs
            );

            return errorResponse(
                    id,
                    -32603,
                    "Tool execution failed: " + exception.getMessage()
            );
        }
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

    private Map<String, Object> kaprukaSearchToolDefinition() {
        return Map.of(
                "name", "kapruka.search",
                "description", "Searches the live Kapruka product catalog for purchasable products.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "userMessage", Map.of("type", "string", "description", "The user's Kapruka shopping request.")
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

    private long elapsedMs(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }
}
