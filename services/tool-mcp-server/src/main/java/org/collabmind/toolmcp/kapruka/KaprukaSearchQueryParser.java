package org.collabmind.toolmcp.kapruka;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KaprukaSearchQueryParser {

    private static final Pattern MAX_PRICE = Pattern.compile(
            "(?i)(?:under|below|less than|max(?:imum)?|budget(?: of| is)?|up to)\\s*(?:rs\\.?|lkr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
    );
    private static final Pattern MIN_PRICE = Pattern.compile(
            "(?i)(?:over|above|more than|min(?:imum)?|from)\\s*(?:rs\\.?|lkr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
    );

    public KaprukaSearchCriteria parse(String userMessage) {
        String message = userMessage == null ? "" : userMessage.trim();
        String query = message
                .replaceAll("(?i)@kapruka\\b", " ")
                .replaceAll("(?i)(?:under|below|less than|max(?:imum)?|budget(?: of| is)?|up to|over|above|more than|min(?:imum)?|from)\\s*(?:rs\\.?|lkr)?\\s*[0-9][0-9,]*(?:\\.[0-9]+)?", " ")
                .replaceAll("(?i)\\b(?:please|show|find|search|recommend|suggest|give me|need|want|looking for|some|anything|something|about|like)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (query.length() < 3) {
            query = "gifts";
        }

        return new KaprukaSearchCriteria(
                query,
                price(MIN_PRICE, message),
                price(MAX_PRICE, message),
                detectCurrency(message),
                8
        );
    }

    private BigDecimal price(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? new BigDecimal(matcher.group(1).replace(",", "")) : null;
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
}
