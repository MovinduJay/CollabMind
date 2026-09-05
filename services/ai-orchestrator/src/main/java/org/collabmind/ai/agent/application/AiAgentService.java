package org.collabmind.ai.agent.application;

import org.collabmind.ai.agent.web.AiContextMessage;
import org.collabmind.ai.agent.web.AiPromptRequest;
import org.collabmind.ai.agent.web.AiPromptResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AiAgentService {

    public AiPromptResponse generateResponse(AiPromptRequest request) {
        List<AiContextMessage> contextMessages = request.contextMessages() == null
                ? List.of()
                : request.contextMessages();

        String contextSummary = buildContextSummary(contextMessages);

        String response = switch (request.agentType()) {
            case PLANNER -> generatePlannerResponse(request.message(), contextSummary);
            case CRITIC -> generateCriticResponse(request.message(), contextSummary);
            case SUMMARIZER -> generateSummarizerResponse(request.message(), contextSummary);
            case RESEARCHER -> generateResearcherResponse(request.message(), contextSummary);
        };

        return new AiPromptResponse(
                request.conversationId(),
                request.userId(),
                request.agentType(),
                response,
                Instant.now()
        );
    }

    private String buildContextSummary(List<AiContextMessage> contextMessages) {
        if (contextMessages.isEmpty()) {
            return "No previous context was provided.";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Recent conversation context:\n");

        for (AiContextMessage message : contextMessages) {
            builder.append("- #")
                    .append(message.sequenceNumber())
                    .append(" [")
                    .append(message.messageType())
                    .append("] ");

            if (message.agentType() != null && !message.agentType().isBlank()) {
                builder.append("(").append(message.agentType()).append(") ");
            }

            builder.append(message.content())
                    .append("\n");
        }

        return builder.toString();
    }

    private String generatePlannerResponse(String message, String contextSummary) {
        return """
                I reviewed the recent conversation context before planning.
                
                %s
                
                Planner response:
                1. Identify the main goal from the discussion.
                2. Break the goal into small implementation tasks.
                3. Prioritize tasks by dependency and risk.
                4. Complete the next smallest useful feature first.
                
                Trigger message: "%s"
                """.formatted(contextSummary, message);
    }

    private String generateCriticResponse(String message, String contextSummary) {
        return """
                I reviewed the recent conversation context before critiquing.
                
                %s
                
                Critic response:
                1. Check whether the current idea is becoming too complex.
                2. Identify the riskiest assumption.
                3. Look for missing validation.
                4. Reduce scope if the feature does not improve the core user flow.
                
                Trigger message: "%s"
                """.formatted(contextSummary, message);
    }

    private String generateSummarizerResponse(String message, String contextSummary) {
        return """
                I reviewed the recent conversation context before summarizing.
                
                %s
                
                Summary response:
                The discussion should be converted into key decisions, open questions, and next actions.
                
                Trigger message: "%s"
                """.formatted(contextSummary, message);
    }

    private String generateResearcherResponse(String message, String contextSummary) {
        return """
                I reviewed the recent conversation context before suggesting research.
                
                %s
                
                Researcher response:
                1. Find similar products or systems.
                2. Compare the target users and use cases.
                3. Validate demand before building too much.
                4. Collect evidence for the next technical decision.
                
                Trigger message: "%s"
                """.formatted(contextSummary, message);
    }
}
