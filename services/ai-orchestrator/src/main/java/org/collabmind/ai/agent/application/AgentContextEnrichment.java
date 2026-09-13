package org.collabmind.ai.agent.application;

public record AgentContextEnrichment(
        String contextSummary,
        String responseAttachment
) {
    public static AgentContextEnrichment contextOnly(String contextSummary) {
        return new AgentContextEnrichment(contextSummary, "");
    }
}
