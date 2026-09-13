package org.collabmind.toolmcp.kapruka;

import org.springframework.stereotype.Service;

@Service
public class KaprukaSearchService {

    private final KaprukaSearchQueryParser queryParser;
    private final KaprukaCatalogPort catalog;

    public KaprukaSearchService(KaprukaSearchQueryParser queryParser, KaprukaCatalogPort catalog) {
        this.queryParser = queryParser;
        this.catalog = catalog;
    }

    public String search(String userMessage, String contextSummary) {
        return catalog.search(queryParser.parse(userMessage, contextSummary));
    }
}
