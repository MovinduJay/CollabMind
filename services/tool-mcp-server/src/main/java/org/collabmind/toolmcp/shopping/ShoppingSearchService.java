package org.collabmind.toolmcp.shopping;

import org.springframework.stereotype.Service;

@Service
public class ShoppingSearchService {

    public String search(String userMessage, String contextSummary) {
        String normalizedMessage = userMessage == null
                ? ""
                : userMessage.toLowerCase();

        String category = detectCategory(normalizedMessage);
        String budget = detectBudget(normalizedMessage);

        return """
                [MCP tool bridge: shopping.search]

                Search intent:
                - Category: %s
                - Budget: %s

                Product-style results:
                1. Wireless earbuds
                   Reason: practical gift, easy to buy, useful for students and professionals.

                2. Minimal desk lamp
                   Reason: good for studying, remote work, and bedroom/desk setups.

                3. Smart water bottle
                   Reason: simple lifestyle gift with broad appeal.

                Buying advice:
                - Choose earbuds if the person likes music, calls, or commuting.
                - Choose a desk lamp if they study or work at a desk.
                - Choose the bottle if you want a safer general-purpose gift.

                Context used:
                %s
                """.formatted(
                category,
                budget,
                safeContext(contextSummary)
        );
    }

    private String detectCategory(String message) {
        if (message.contains("birthday")) {
            return "birthday gift";
        }

        if (message.contains("tech") || message.contains("gadget")) {
            return "tech gift";
        }

        if (message.contains("office") || message.contains("work")) {
            return "office gift";
        }

        if (message.contains("student") || message.contains("study")) {
            return "student gift";
        }

        return "general gift";
    }

    private String detectBudget(String message) {
        if (message.contains("10000") || message.contains("10,000")) {
            return "Rs. 10,000";
        }

        if (message.contains("5000") || message.contains("5,000")) {
            return "Rs. 5,000";
        }

        if (message.contains("20000") || message.contains("20,000")) {
            return "Rs. 20,000";
        }

        return "not specified";
    }

    private String safeContext(String contextSummary) {
        if (contextSummary == null || contextSummary.isBlank()) {
            return "No previous context provided.";
        }

        if (contextSummary.length() <= 500) {
            return contextSummary;
        }

        return contextSummary.substring(0, 500) + "... [context truncated]";
    }
}
