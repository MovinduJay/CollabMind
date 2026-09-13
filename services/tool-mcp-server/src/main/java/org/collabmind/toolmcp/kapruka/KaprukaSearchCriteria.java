package org.collabmind.toolmcp.kapruka;

import java.math.BigDecimal;

public record KaprukaSearchCriteria(
        String query,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String currency,
        int limit
) {
}
