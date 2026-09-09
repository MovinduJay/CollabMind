package org.collabmind.ai.tool.application;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class MockShoppingToolProvider implements AiToolProvider {

    private static final String TOOL_NAME = "shopping.search";

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public boolean supports(String toolName) {
        return TOOL_NAME.equalsIgnoreCase(toolName);
    }

    @Override
    public ToolCallResponse invoke(ToolCallRequest request) {
        Instant startedAt = Instant.now();

        String message = request.userMessage() == null
                ? ""
                : request.userMessage().toLowerCase();

        String recommendationType = "general gift";

        if (message.contains("birthday")) {
            recommendationType = "birthday gift";
        } else if (message.contains("tech") || message.contains("gadget")) {
            recommendationType = "tech gift";
        } else if (message.contains("office") || message.contains("work")) {
            recommendationType = "office gift";
        }

        String budget = "the requested budget";

        if (message.contains("10000") || message.contains("10,000")) {
            budget = "Rs. 10,000";
        } else if (message.contains("5000") || message.contains("5,000")) {
            budget = "Rs. 5,000";
        }

        String result = """
                [Mock tool: shopping.search]

                Search intent:
                - Category: %s
                - Budget: %s

                Suggested options:
                1. Premium wireless earbuds — practical, popular, and easy to gift.
                2. Minimal desk lamp — useful for students, remote workers, and office setups.
                3. Smart water bottle — simple lifestyle gift with broad appeal.

                Tool note:
                This is currently a mock provider. It is designed so a real MCP shopping provider can replace it later without changing the AI orchestration flow.
                """.formatted(
                recommendationType,
                budget
        );

        return ToolCallResponse.success(
                TOOL_NAME,
                result,
                Duration.between(startedAt, Instant.now()).toMillis()
        );
    }
}
