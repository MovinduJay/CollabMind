package org.collabmind.ai.agent.application;

import org.collabmind.ai.agent.domain.AgentType;
import org.collabmind.ai.agent.web.AiPromptRequest;
import org.collabmind.ai.tool.application.AiToolService;
import org.collabmind.ai.tool.application.ToolCallRequest;
import org.collabmind.ai.tool.application.ToolCallResponse;
import org.springframework.stereotype.Component;

@Component
public class KaprukaAgentContextEnricher implements AgentContextEnricher {

    private final AiToolService aiToolService;

    public KaprukaAgentContextEnricher(AiToolService aiToolService) {
        this.aiToolService = aiToolService;
    }

    @Override
    public boolean supports(AgentType agentType) {
        return agentType == AgentType.KAPRUKA;
    }

    @Override
    public AgentContextEnrichment enrich(AiPromptRequest request, String contextSummary) {
        ToolCallResponse response = aiToolService.invoke(new ToolCallRequest(
                request.conversationId(),
                request.userId(),
                "kapruka.search",
                request.message(),
                contextSummary
        ));
        if (!response.success()) {
            throw new IllegalStateException("Live Kapruka catalog search failed: " + response.errorMessage());
        }
        String catalogJson = response.result().trim();
        return new AgentContextEnrichment(
                contextSummary + "\n\nLIVE KAPRUKA MCP RESULTS (authoritative JSON):\n" + catalogJson,
                "[[KAPRUKA_PRODUCTS]]" + catalogJson + "[[/KAPRUKA_PRODUCTS]]"
        );
    }
}
