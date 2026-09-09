package org.collabmind.toolmcp.github;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitHubToolService {

    private static final Pattern REPO_PATTERN = Pattern.compile("(?i)([a-z0-9_.-]+/[a-z0-9_.-]+)");

    private final RestClient restClient;
    private final String githubToken;

    public GitHubToolService(
            RestClient.Builder restClientBuilder,
            @Value("${collabmind.github.token:}") String githubToken
    ) {
        this.restClient = restClientBuilder.build();
        this.githubToken = githubToken;
    }

    public String repoSummary(String userMessage, String contextSummary) {
        Optional<String> repoSlug = extractRepoSlug(userMessage);

        if (repoSlug.isEmpty()) {
            return """
                    [MCP tool bridge: github.repo_summary]

                    I could not find a repository name in the message.

                    Use this format:
                    @github summarize owner/repo

                    Example:
                    @github summarize octocat/Hello-World
                    """;
        }

        try {
            String[] parts = repoSlug.get().split("/");
            String owner = parts[0];
            String repo = parts[1];

            JsonNode repoResponse = restClient
                    .get()
                    .uri("https://api.github.com/repos/{owner}/{repo}", owner, repo)
                    .headers(this::addGitHubHeaders)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode issuesResponse = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.github.com")
                            .path("/repos/{owner}/{repo}/issues")
                            .queryParam("state", "open")
                            .queryParam("per_page", 5)
                            .build(owner, repo))
                    .headers(this::addGitHubHeaders)
                    .retrieve()
                    .body(JsonNode.class);

            return """
                    [MCP tool bridge: github.repo_summary]

                    Repository:
                    %s

                    Description:
                    %s

                    Main facts:
                    - Language: %s
                    - Stars: %d
                    - Forks: %d
                    - Open issues: %d
                    - Default branch: %s

                    Recent open issues:
                    %s

                    Context used:
                    %s
                    """.formatted(
                    repoResponse.path("full_name").asText(repoSlug.get()),
                    repoResponse.path("description").asText("No description provided."),
                    repoResponse.path("language").asText("Unknown"),
                    repoResponse.path("stargazers_count").asInt(0),
                    repoResponse.path("forks_count").asInt(0),
                    repoResponse.path("open_issues_count").asInt(0),
                    repoResponse.path("default_branch").asText("main"),
                    formatIssues(issuesResponse),
                    safeContext(contextSummary)
            );
        } catch (Exception exception) {
            return """
                    [MCP tool bridge: github.repo_summary]

                    GitHub API call failed for:
                    %s

                    Reason:
                    %s

                    This tool works with public GitHub repositories without a token. For private repositories or higher rate limits, configure GITHUB_TOKEN.
                    """.formatted(
                    repoSlug.get(),
                    exception.getMessage()
            );
        }
    }

    public String searchIssues(String userMessage, String contextSummary) {
        Optional<String> repoSlug = extractRepoSlug(userMessage);

        if (repoSlug.isEmpty()) {
            return """
                    [MCP tool bridge: github.search_issues]

                    I could not find a repository name in the message.

                    Use this format:
                    @github search issues in owner/repo about login bug

                    Example:
                    @github search issues in spring-projects/spring-petclinic about docker
                    """;
        }

        String query = extractIssueSearchQuery(userMessage, repoSlug.get());

        try {
            String githubQuery = "repo:" + repoSlug.get() + " type:issue " + query;

            JsonNode searchResponse = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.github.com")
                            .path("/search/issues")
                            .queryParam("q", githubQuery)
                            .queryParam("per_page", 5)
                            .build())
                    .headers(this::addGitHubHeaders)
                    .retrieve()
                    .body(JsonNode.class);

            return """
                    [MCP tool bridge: github.search_issues]

                    Repository:
                    %s

                    Search query:
                    %s

                    Total matches:
                    %d

                    Top issue results:
                    %s

                    Context used:
                    %s
                    """.formatted(
                    repoSlug.get(),
                    query,
                    searchResponse.path("total_count").asInt(0),
                    formatIssueSearchResults(searchResponse.path("items")),
                    safeContext(contextSummary)
            );
        } catch (Exception exception) {
            return """
                    [MCP tool bridge: github.search_issues]

                    GitHub issue search failed for:
                    %s

                    Query:
                    %s

                    Reason:
                    %s
                    """.formatted(
                    repoSlug.get(),
                    query,
                    exception.getMessage()
            );
        }
    }

    private Optional<String> extractRepoSlug(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = REPO_PATTERN.matcher(text);

        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }

        return Optional.empty();
    }

    private String extractIssueSearchQuery(String userMessage, String repoSlug) {
        if (userMessage == null || userMessage.isBlank()) {
            return "bug";
        }

        String cleaned = userMessage
                .replaceAll("(?i)@github", " ")
                .replace(repoSlug, " ")
                .replaceAll("(?i)search", " ")
                .replaceAll("(?i)issues", " ")
                .replaceAll("(?i)issue", " ")
                .replaceAll("(?i)in", " ")
                .replaceAll("(?i)about", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.isBlank()) {
            return "bug";
        }

        return cleaned;
    }

    private String formatIssues(JsonNode issues) {
        if (issues == null || !issues.isArray() || issues.isEmpty()) {
            return "- No open issues returned.";
        }

        StringBuilder builder = new StringBuilder();
        int count = 0;

        for (JsonNode issue : issues) {
            if (issue.has("pull_request")) {
                continue;
            }

            count++;

            builder.append("- #")
                    .append(issue.path("number").asInt())
                    .append(" ")
                    .append(issue.path("title").asText("Untitled issue"))
                    .append("\n");

            if (count >= 5) {
                break;
            }
        }

        if (builder.isEmpty()) {
            return "- No open issues returned.";
        }

        return builder.toString().trim();
    }

    private String formatIssueSearchResults(JsonNode items) {
        if (items == null || !items.isArray() || items.isEmpty()) {
            return "- No matching issues returned.";
        }

        StringBuilder builder = new StringBuilder();

        for (JsonNode item : items) {
            builder.append("- #")
                    .append(item.path("number").asInt())
                    .append(" ")
                    .append(item.path("title").asText("Untitled issue"))
                    .append("\n");
        }

        return builder.toString().trim();
    }

    private String safeContext(String contextSummary) {
        if (contextSummary == null || contextSummary.isBlank()) {
            return "No previous context provided.";
        }

        if (contextSummary.length() <= 500) {
            return contextSummary;
        }

        return contextSummary.substring(0, 500) + "... [context truncated]";
    }

    private void addGitHubHeaders(HttpHeaders headers) {
        headers.add("Accept", "application/vnd.github+json");
        headers.add("User-Agent", "CollabMind-Tool-MCP-Server");

        if (githubToken != null && !githubToken.isBlank()) {
            headers.setBearerAuth(githubToken);
        }
    }
}
