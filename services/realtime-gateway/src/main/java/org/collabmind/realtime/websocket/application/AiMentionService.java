package org.collabmind.realtime.websocket.application;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class AiMentionService {

    private static final Map<String, String> AGENT_MENTIONS = Map.of(
            "@planner", "PLANNER",
            "@critic", "CRITIC",
            "@summarizer", "SUMMARIZER",
            "@researcher", "RESEARCHER",
            "@shopping", "SHOPPING",
            "@github", "GITHUB"
    );

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

        String normalizedContent = content.toLowerCase();

        return AGENT_MENTIONS
                .entrySet()
                .stream()
                .filter(entry -> normalizedContent.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public boolean hasAiMention(String content) {
        return findMentionedAgentType(content).isPresent();
    }
}
