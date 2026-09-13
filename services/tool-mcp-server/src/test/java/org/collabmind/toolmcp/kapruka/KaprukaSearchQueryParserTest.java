package org.collabmind.toolmcp.kapruka;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KaprukaSearchQueryParserTest {

    private final KaprukaSearchQueryParser parser = new KaprukaSearchQueryParser();

    @Test
    void parsesMentionQueryBudgetAndCurrency() {
        KaprukaSearchCriteria criteria = parser.parse("@kapruka find birthday gifts under Rs. 10,000");

        assertThat(criteria.query()).isEqualTo("birthday gifts");
        assertThat(criteria.maxPrice()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(criteria.currency()).isEqualTo("LKR");
        assertThat(criteria.limit()).isEqualTo(8);
    }

    @Test
    void usesSafeGiftFallbackForAnEmptyIntent() {
        assertThat(parser.parse("@kapruka show me something").query()).isEqualTo("gifts");
    }

    @Test
    void extractsProductTermsFromConversationalCakeSearch() {
        assertThat(parser.parse("@kapruka show me birthday cakes under Rs. 10,000").query())
                .isEqualTo("birthday cakes");
    }

    @Test
    void expandsKSuffixAndRemovesItFromTheSearchQuery() {
        KaprukaSearchCriteria criteria = parser.parse("@kapruka need cakes under 10k");

        assertThat(criteria.query()).isEqualTo("cakes");
        assertThat(criteria.maxPrice()).isEqualByComparingTo(new BigDecimal("10000"));
    }

    @Test
    void correctsCommonTyposAndRemovesArticles() {
        KaprukaSearchCriteria criteria = parser.parse("@kapruka ned a cake under 10k");

        assertThat(criteria.query()).isEqualTo("cake");
        assertThat(criteria.maxPrice()).isEqualByComparingTo(new BigDecimal("10000"));
    }

    @Test
    void carriesProductIntentIntoARefinement() {
        KaprukaSearchCriteria criteria = parser.parse(
                "@kapruka show cheaper ones under 8k",
                "Recent conversation context:\n- #1 [USER] @kapruka need birthday cakes under 10k"
        );

        assertThat(criteria.query()).isEqualTo("birthday cakes");
        assertThat(criteria.maxPrice()).isEqualByComparingTo(new BigDecimal("8000"));
    }
}
