package org.collabmind.ai.provider.gemini;

import java.util.List;

public record GeminiGenerateContentRequest(
        List<GeminiContent> contents
) {

    public static GeminiGenerateContentRequest fromText(String prompt) {
        return new GeminiGenerateContentRequest(
                List.of(
                        new GeminiContent(
                                "user",
                                List.of(new GeminiPart(prompt))
                        )
                )
        );
    }

    public record GeminiContent(
            String role,
            List<GeminiPart> parts
    ) {
    }

    public record GeminiPart(
            String text
    ) {
    }
}
