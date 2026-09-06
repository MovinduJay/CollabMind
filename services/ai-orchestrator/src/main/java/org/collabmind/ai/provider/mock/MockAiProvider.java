package org.collabmind.ai.provider.mock;

import org.collabmind.ai.agent.web.AiPromptRequest;
import org.collabmind.ai.provider.application.AiProvider;
import org.springframework.stereotype.Component;

@Component
public class MockAiProvider implements AiProvider {

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public String generateResponse(
            AiPromptRequest request,
            String contextSummary
    ) {
        return switch (request.agentType()) {
            case PLANNER -> generatePlannerResponse(request.message(), contextSummary);
            case CRITIC -> generateCriticResponse(request.message(), contextSummary);
            case SUMMARIZER -> generateSummarizerResponse(request.message(), contextSummary);
            case RESEARCHER -> generateResearcherResponse(request.message(), contextSummary);
        };
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
