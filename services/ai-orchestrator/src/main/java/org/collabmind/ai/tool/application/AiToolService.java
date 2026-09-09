package org.collabmind.ai.tool.application;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AiToolService {

    private final List<AiToolProvider> toolProviders;

    public AiToolService(List<AiToolProvider> toolProviders) {
        this.toolProviders = toolProviders;
    }

    public ToolCallResponse invoke(ToolCallRequest request) {
        Instant startedAt = Instant.now();

        return toolProviders
                .stream()
                .filter(provider -> provider.supports(request.toolName()))
                .findFirst()
                .map(provider -> provider.invoke(request))
                .orElseGet(() -> ToolCallResponse.failed(
                        request.toolName(),
                        "No tool provider found for tool: " + request.toolName(),
                        Duration.between(startedAt, Instant.now()).toMillis()
                ));
    }
}
