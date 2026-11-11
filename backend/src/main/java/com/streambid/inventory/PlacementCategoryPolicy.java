package com.streambid.inventory;

import java.util.Set;

public record PlacementCategoryPolicy(boolean unrestricted, Set<String> categories) {
    public PlacementCategoryPolicy {
        categories = Set.copyOf(categories);
        if (categories.size() > 20 || (unrestricted && !categories.isEmpty()) || (!unrestricted && categories.isEmpty())) {
            throw new IllegalArgumentException("Choose unrestricted advertising or one to twenty categories");
        }
        if (categories.stream().anyMatch(value -> value.length() > 40 || !value.matches("[a-z0-9]+(-[a-z0-9]+)*"))) {
            throw new IllegalArgumentException("Advertising categories must be lowercase slugs of up to forty characters");
        }
    }
}
