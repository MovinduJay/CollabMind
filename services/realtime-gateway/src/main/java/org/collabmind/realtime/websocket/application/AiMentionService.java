package org.collabmind.realtime.websocket.application;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AiMentionService {

    public Optional<String> detectAgentType(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        String normalizedMessage = message.toLowerCase();

        if (normalizedMessage.contains("@planner")) {
            return Optional.of("PLANNER");
        }

        if (normalizedMessage.contains("@critic")) {
            return Optional.of("CRITIC");
        }

        if (normalizedMessage.contains("@summarizer")) {
            return Optional.of("SUMMARIZER");
        }

        if (normalizedMessage.contains("@researcher")) {
            return Optional.of("RESEARCHER");
        }

        return Optional.empty();
    }
}
