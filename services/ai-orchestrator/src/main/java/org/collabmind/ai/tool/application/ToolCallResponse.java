package org.collabmind.ai.tool.application;

public record ToolCallResponse(
        String toolName,
        boolean success,
        String result,
        String errorMessage,
        long latencyMs
) {
    public static ToolCallResponse success(
            String toolName,
            String result,
            long latencyMs
    ) {
        return new ToolCallResponse(
                toolName,
                true,
                result,
                null,
                latencyMs
        );
    }

    public static ToolCallResponse failed(
            String toolName,
            String errorMessage,
            long latencyMs
    ) {
        return new ToolCallResponse(
                toolName,
                false,
                null,
                errorMessage,
                latencyMs
        );
    }
}
