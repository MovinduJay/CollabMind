package org.collabmind.ai.agent.application;

import org.collabmind.ai.agent.domain.AgentType;
import org.collabmind.ai.agent.web.AiPromptRequest;
import org.collabmind.ai.agent.web.AiPromptResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AiAgentService {

    public AiPromptResponse generateResponse(AiPromptRequest request) {
        String response = switch (request.agentType()) {
            case PLANNER -> generatePlannerResponse(request.message());
            case CRITIC -> generateCriticResponse(request.message());
            case SUMMARIZER -> generateSummarizerResponse(request.message());
            case RESEARCHER -> generateResearcherResponse(request.message());
        };

        return new AiPromptResponse(
                request.conversationId(),
                request.userId(),
                request.agentType(),
                response,
                Instant.now()
        );
    }

    private String generatePlannerResponse(String message) {
        return """
                Here is a simple plan:
                1. Clarify the main goal.
                2. Break the idea into small tasks.
                3. Assign priorities.
                4. Decide the next action.
                
                Based on your message: "%s"
                """.formatted(message);
    }

    private String generateCriticResponse(String message) {
        return """
                Here is a critical review:
                1. Check whether the idea solves a real problem.
                2. Identify the riskiest assumption.
                3. Look for missing constraints.
                4. Validate before building too much.
                
                Based on your message: "%s"
                """.formatted(message);
    }

    private String generateSummarizerResponse(String message) {
        return """
                Summary:
                The discussion needs to be converted into a clear decision, key points, and next steps.
                
                Source message: "%s"
                """.formatted(message);
    }

    private String generateResearcherResponse(String message) {
        return """
                Research direction:
                1. Find similar existing solutions.
                2. Compare target users.
                3. Check market demand.
                4. Collect evidence before implementation.
                
                Based on your message: "%s"
                """.formatted(message);
    }
}
