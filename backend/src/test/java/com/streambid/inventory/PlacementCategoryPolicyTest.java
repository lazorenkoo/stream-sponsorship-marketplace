package com.streambid.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlacementCategoryPolicyTest {
    @Test
    void categoryRestrictionsAreExplicitAndImmutable() {
        var selected = new HashSet<>(Set.of("gaming", "food-and-drink"));
        var policy = new PlacementCategoryPolicy(false, selected);
        selected.clear();
        assertThat(policy.categories()).hasSize(2);
        assertThatThrownBy(() -> new PlacementCategoryPolicy(false, Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlacementCategoryPolicy(true, Set.of("gaming"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlacementCategoryPolicy(false, Set.of("Invalid Category"))).isInstanceOf(IllegalArgumentException.class);
    }
}
