package org.collabmind.realtime.websocket.application;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AiMentionService {

    private static final Pattern REPO_PATTERN =
            Pattern.compile("(?i)[a-z0-9_.-]+/[a-z0-9_.-]+");

    public Optional<String> detectAgentType(String content) {
        return findMentionedAgentType(content);
    }

    public Optional<String> findAgentType(String content) {
        return findMentionedAgentType(content);
    }

    public Optional<String> extractAgentType(String content) {
        return findMentionedAgentType(content);
    }

    public Optional<String> detectMentionedAgentType(String content) {
        return findMentionedAgentType(content);
    }

    public Optional<String> extractMentionedAgentType(String content) {
        return findMentionedAgentType(content);
    }

    public Optional<String> findMentionedAgentType(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        String normalized = content.toLowerCase();

        if (normalized.matches("(?s).*@kapruka\\b.*")) {
            return Optional.of("KAPRUKA");
        }

        if (!normalized.contains("@ai")) {
            return Optional.empty();
        }

        if (looksLikeGithubRequest(normalized)) {
            return Optional.of("GITHUB");
        }

        if (looksLikeShoppingRequest(normalized)) {
            return Optional.of("SHOPPING");
        }

        if (looksLikeSummaryRequest(normalized)) {
            return Optional.of("SUMMARIZER");
        }

        if (looksLikeCriticRequest(normalized)) {
            return Optional.of("CRITIC");
        }

        if (looksLikePlanningRequest(normalized)) {
            return Optional.of("PLANNER");
        }

        return Optional.of("RESEARCHER");
    }

    public boolean hasAiMention(String content) {
        return findMentionedAgentType(content).isPresent();
    }

    private boolean looksLikeGithubRequest(String content) {
        return content.contains("github")
                || content.contains("repo")
                || content.contains("repository")
                || content.contains("issue")
                || content.contains("pull request")
                || content.contains("pr ")
                || REPO_PATTERN.matcher(content).find();
    }

    private boolean looksLikeShoppingRequest(String content) {
        return content.contains("shopping")
                || content.contains("buy")
                || content.contains("gift")
                || content.contains("restaurant")
                || content.contains("places to eat")
                || content.contains("food")
                || content.contains("sushi")
                || content.contains("product")
                || content.contains("price")
                || content.contains("budget")
                || content.contains("rs.")
                || content.contains("lkr");
    }

    private boolean looksLikeSummaryRequest(String content) {
        return content.contains("summarize")
                || content.contains("summary")
                || content.contains("recap")
                || content.contains("what did we decide");
    }

    private boolean looksLikeCriticRequest(String content) {
        return content.contains("critic")
                || content.contains("risk")
                || content.contains("risks")
                || content.contains("weakness")
                || content.contains("problem")
                || content.contains("what could go wrong");
    }

    private boolean looksLikePlanningRequest(String content) {
        return content.contains("plan")
                || content.contains("steps")
                || content.contains("tasks")
                || content.contains("roadmap")
                || content.contains("next");
    }
}
