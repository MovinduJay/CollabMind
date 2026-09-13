package org.collabmind.ai.agent.application;

import org.collabmind.ai.agent.domain.AgentType;
import org.collabmind.ai.agent.web.AiPromptRequest;

public interface AgentContextEnricher {

    boolean supports(AgentType agentType);

    AgentContextEnrichment enrich(AiPromptRequest request, String contextSummary);
}
