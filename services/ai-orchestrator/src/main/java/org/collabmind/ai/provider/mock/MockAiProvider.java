package org.collabmind.ai.provider.mock;

import org.collabmind.ai.provider.application.AiProvider;
import org.collabmind.ai.agent.web.AiPromptRequest;
import org.collabmind.ai.tool.application.AiToolService;
import org.collabmind.ai.tool.application.ToolCallRequest;
import org.collabmind.ai.tool.application.ToolCallResponse;
import org.springframework.stereotype.Component;

@Component
public class MockAiProvider implements AiProvider {

    private final AiToolService aiToolService;

    public MockAiProvider(AiToolService aiToolService) {
        this.aiToolService = aiToolService;
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public String generateResponse(
            AiPromptRequest request,
            String contextSummary
    ) {
        String agentType = normalizeAgentType(request.agentType() == null ? null : request.agentType().name());

        return switch (agentType) {
            case "PLANNER" -> plannerResponse(request, contextSummary);
            case "CRITIC" -> criticResponse(request, contextSummary);
            case "SUMMARIZER" -> summarizerResponse(request, contextSummary);
            case "RESEARCHER" -> researcherResponse(request, contextSummary);
            case "SHOPPING" -> shoppingResponse(request, contextSummary);
            case "GITHUB" -> githubResponse(request, contextSummary);
            case "KAPRUKA" -> kaprukaResponse(contextSummary);
            default -> defaultResponse(request, contextSummary);
        };
    }

    private String plannerResponse(
            AiPromptRequest request,
            String contextSummary
    ) {
        return """
                Planner response:

                Based on your message:
                %s

                Suggested next tasks:
                1. Clarify the exact goal and expected output.
                2. Break the work into small backend milestones.
                3. Implement one testable feature and verify it end-to-end.

                Context considered:
                %s
                """.formatted(
                request.message(),
                safeContext(contextSummary)
        );
    }

    private String criticResponse(
            AiPromptRequest request,
            String contextSummary
    ) {
        return """
                Critic response:

                Main risks:
                1. The feature may be too broad without a clear boundary.
                2. Error handling and retry behaviour need to be explicit.
                3. Testing should cover both success and failure paths.

                Message reviewed:
                %s

                Context considered:
                %s
                """.formatted(
                request.message(),
                safeContext(contextSummary)
        );
    }

    private String summarizerResponse(
            AiPromptRequest request,
            String contextSummary
    ) {
        return """
                Summary response:

                The current discussion is about:
                %s

                Key context:
                %s
                """.formatted(
                request.message(),
                safeContext(contextSummary)
        );
    }

    private String researcherResponse(
            AiPromptRequest request,
            String contextSummary
    ) {
        return """
                Researcher response:

                Research direction:
                1. Identify the core technical question.
                2. Compare existing approaches.
                3. Extract implementation constraints.
                4. Recommend the smallest useful experiment.

                User message:
                %s

                Context considered:
                %s
                """.formatted(
                request.message(),
                safeContext(contextSummary)
        );
    }

    private String shoppingResponse(
            AiPromptRequest request,
            String contextSummary
    ) {
        ToolCallResponse toolResponse = aiToolService.invoke(
                new ToolCallRequest(
                        request.conversationId(),
                        request.userId(),
                        "shopping.search",
                        request.message(),
                        contextSummary
                )
        );

        if (!toolResponse.success()) {
            return """
                    Shopping agent response:

                    I tried to call the shopping tool, but it failed.

                    Tool:
                    %s

                    Error:
                    %s
                    """.formatted(
                    toolResponse.toolName(),
                    toolResponse.errorMessage()
            );
        }

        return """
                Shopping agent response:

                I used the shopping tool provider to understand the request.

                Tool:
                %s

                Tool latency:
                %d ms

                Tool result:
                %s

                Final recommendation:
                Start with option 1 if you want the safest gift. Pick option 2 if the person studies or works at a desk often. Pick option 3 if you want a simple lifestyle gift.
                """.formatted(
                toolResponse.toolName(),
                toolResponse.latencyMs(),
                toolResponse.result()
        );
    }

    private String githubResponse(
            AiPromptRequest request,
            String contextSummary
    ) {
        String message = request.message() == null
                ? ""
                : request.message().toLowerCase();

        String toolName = message.contains("issue") || message.contains("bug")
                ? "github.search_issues"
                : "github.repo_summary";

        ToolCallResponse toolResponse = aiToolService.invoke(
                new ToolCallRequest(
                        request.conversationId(),
                        request.userId(),
                        toolName,
                        request.message(),
                        contextSummary
                )
        );

        if (!toolResponse.success()) {
            return """
                    GitHub agent response:

                    I tried to call the GitHub tool, but it failed.

                    Tool:
                    %s

                    Error:
                    %s
                    """.formatted(
                    toolResponse.toolName(),
                    toolResponse.errorMessage()
            );
        }

        return """
                GitHub agent response:

                I used the GitHub MCP tool bridge to inspect the repository.

                Tool:
                %s

                Tool latency:
                %d ms

                Tool result:
                %s
                """.formatted(
                toolResponse.toolName(),
                toolResponse.latencyMs(),
                toolResponse.result()
        );
    }

    private String kaprukaResponse(String contextSummary) {
        return "Kapruka Agent\n\n" + safeContext(contextSummary);
    }
    private String defaultResponse(
            AiPromptRequest request,
            String contextSummary
    ) {
        return """
                Mock AI response:

                Agent:
                %s

                Message:
                %s

                Context considered:
                %s
                """.formatted(
                request.agentType(),
                request.message(),
                safeContext(contextSummary)
        );
    }

    private String normalizeAgentType(String agentType) {
        if (agentType == null || agentType.isBlank()) {
            return "UNKNOWN";
        }

        return agentType.trim().toUpperCase();
    }

    private String safeContext(String contextSummary) {
        if (contextSummary == null || contextSummary.isBlank()) {
            return "No previous context.";
        }

        return contextSummary;
    }
}


