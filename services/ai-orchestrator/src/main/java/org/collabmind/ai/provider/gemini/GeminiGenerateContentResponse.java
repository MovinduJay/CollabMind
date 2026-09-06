package org.collabmind.ai.provider.gemini;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record GeminiGenerateContentResponse(
        List<GeminiCandidate> candidates
) {

    public String firstText() {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini returned no candidates");
        }

        GeminiCandidate firstCandidate = candidates.getFirst();

        if (
                firstCandidate.content() == null ||
                firstCandidate.content().parts() == null ||
                firstCandidate.content().parts().isEmpty()
        ) {
            throw new IllegalStateException("Gemini returned no text parts");
        }

        String text = firstCandidate.content()
                .parts()
                .stream()
                .map(GeminiPart::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"))
                .trim();

        if (text.isBlank()) {
            throw new IllegalStateException("Gemini returned blank text");
        }

        return text;
    }

    public record GeminiCandidate(
            GeminiContent content
    ) {
    }

    public record GeminiContent(
            List<GeminiPart> parts
    ) {
    }

    public record GeminiPart(
            String text
    ) {
    }
}
