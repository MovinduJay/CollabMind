package org.collabmind.ai.provider.mock;

import org.collabmind.ai.agent.web.AiPromptRequest;
import org.collabmind.ai.provider.application.AiProvider;
import org.springframework.stereotype.Component;

@Component
public class FailingAiProvider implements AiProvider {

    @Override
    public String providerName() {
        return "failing";
    }

    @Override
    public String generateResponse(
            AiPromptRequest request,
            String contextSummary
    ) {
        throw new IllegalStateException("Simulated AI provider failure");
    }
}
