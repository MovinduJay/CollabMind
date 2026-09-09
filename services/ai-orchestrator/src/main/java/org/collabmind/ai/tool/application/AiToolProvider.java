package org.collabmind.ai.tool.application;

public interface AiToolProvider {

    String toolName();

    boolean supports(String toolName);

    ToolCallResponse invoke(ToolCallRequest request);
}
