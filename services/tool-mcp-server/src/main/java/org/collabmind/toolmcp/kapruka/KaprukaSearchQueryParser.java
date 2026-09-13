package org.collabmind.toolmcp.kapruka;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class KaprukaSearchQueryParser {

    private static final Pattern MAX_PRICE = Pattern.compile("(?i)(?:under|below|less than|max(?:imum)?|budget(?: of| is)?|up to)\\s*(?:rs\\.?|lkr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)(k)?\\b");
    private static final Pattern MIN_PRICE = Pattern.compile("(?i)(?:over|above|more than|min(?:imum)?|from)\\s*(?:rs\\.?|lkr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)(k)?\\b");
    private static final Pattern PRICE_CLAUSE = Pattern.compile("(?i)(?:under|below|less than|max(?:imum)?|budget(?: of| is)?|up to|over|above|more than|min(?:imum)?|from)\\s*(?:rs\\.?|lkr)?\\s*[0-9][0-9,]*(?:\\.[0-9]+)?k?\\b");
    private static final Set<String> FILLER_WORDS = Set.of(
            "a", "an", "the", "i", "im", "me", "please", "show", "find", "search", "recommend",
            "suggest", "give", "need", "want", "looking", "for", "some", "anything", "something",
            "about", "like", "products", "product", "options", "option", "ones", "one", "more",
            "cheaper", "cheap", "available", "kapruka", "rs", "lkr"
    );
    private static final Map<String, String> TYPO_ALIASES = Map.ofEntries(
            Map.entry("ned", "need"), Map.entry("nead", "need"), Map.entry("neeed", "need"),
            Map.entry("wana", "want"), Map.entry("wanna", "want"),
            Map.entry("cke", "cake"), Map.entry("cak", "cake")
    );

    public KaprukaSearchCriteria parse(String userMessage) {
        return parse(userMessage, "");
    }

    public KaprukaSearchCriteria parse(String userMessage, String contextSummary) {
        String message = safe(userMessage);
        String previousRequest = previousKaprukaRequest(message, contextSummary);
        String query = extractProductTerms(message);
        if (query.isBlank()) query = extractProductTerms(previousRequest);
        if (query.length() < 3) query = "gifts";

        return new KaprukaSearchCriteria(
                query,
                firstPrice(MIN_PRICE, message, previousRequest),
                firstPrice(MAX_PRICE, message, previousRequest),
                detectCurrency(message + " " + previousRequest),
                8
        );
    }

    private String extractProductTerms(String message) {
        String normalized = PRICE_CLAUSE.matcher(message).replaceAll(" ")
                .replaceAll("(?i)@kapruka\\b", " ")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ");
        return Arrays.stream(normalized.trim().split("\\s+"))
                .map(token -> TYPO_ALIASES.getOrDefault(token, token))
                .filter(token -> !token.isBlank() && !FILLER_WORDS.contains(token))
                .filter(token -> !token.matches("[0-9]+"))
                .collect(Collectors.joining(" "));
    }

    private String previousKaprukaRequest(String currentMessage, String contextSummary) {
        String[] lines = safe(contextSummary).split("\\R");
        for (int index = lines.length - 1; index >= 0; index--) {
            String line = lines[index];
            int mention = line.toLowerCase(Locale.ROOT).indexOf("@kapruka");
            if (mention >= 0) {
                String candidate = line.substring(mention).strip();
                if (!candidate.equalsIgnoreCase(currentMessage.strip())) return candidate;
            }
        }
        return "";
    }

    private BigDecimal firstPrice(Pattern pattern, String current, String previous) {
        BigDecimal currentPrice = price(pattern, current);
        return currentPrice == null ? price(pattern, previous) : currentPrice;
    }

    private BigDecimal price(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) return null;
        BigDecimal amount = new BigDecimal(matcher.group(1).replace(",", ""));
        return matcher.group(2) == null ? amount : amount.multiply(BigDecimal.valueOf(1000));
    }

    private String detectCurrency(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("usd") || normalized.contains("$")) return "USD";
        if (normalized.contains("gbp") || normalized.contains("£")) return "GBP";
        if (normalized.contains("eur") || normalized.contains("€")) return "EUR";
        if (normalized.contains("aud")) return "AUD";
        if (normalized.contains("cad")) return "CAD";
        return "LKR";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
