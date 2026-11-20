package com.company.ems.search;

import java.util.List;
import java.util.Map;

public final class SearchDtos {

    private SearchDtos() {
    }

    public record Item(String type,
                       Long id,
                       String title,
                       String subtitle,
                       String snippet,
                       String link,
                       String highlightedTitle,
                       String highlightedSnippet) {
    }

    public record Response(String keyword,
                           long total,
                           long page,
                           long size,
                           List<String> types,
                           Map<String, Integer> counts,
                           List<Item> items) {
    }
}
