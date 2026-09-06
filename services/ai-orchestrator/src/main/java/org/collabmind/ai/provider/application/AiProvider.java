package org.collabmind.ai.provider.application;

import org.collabmind.ai.agent.web.AiPromptRequest;

public interface AiProvider {

    String providerName();

    String generateResponse(
            AiPromptRequest request,
            String contextSummary
    );
}
