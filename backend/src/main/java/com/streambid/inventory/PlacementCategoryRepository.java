package com.streambid.inventory;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PlacementCategoryRepository {
    private final DSLContext database;

    public PlacementCategoryRepository(DSLContext database) {
        this.database = database;
    }

    public Optional<PlacementCategoryPolicy> findOwned(UUID placementId, UUID owner) {
        return database.fetchOptional("""
                SELECT p.unrestricted_categories,
                    ARRAY(SELECT category FROM placement_advertising_categories WHERE placement_id = p.id ORDER BY category) AS categories
                FROM ad_placements p JOIN connected_channels c ON c.id = p.channel_id WHERE p.id = ? AND c.user_id = ?
                """, placementId, owner).map(row -> new PlacementCategoryPolicy(row.get("unrestricted_categories", Boolean.class),
                        Set.of(row.get("categories", String[].class))));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replace(UUID placementId, PlacementCategoryPolicy policy) {
        database.execute("DELETE FROM placement_advertising_categories WHERE placement_id = ?", placementId);
        database.execute("UPDATE ad_placements SET unrestricted_categories = ? WHERE id = ?", policy.unrestricted(), placementId);
        for (String category : policy.categories().stream().sorted().toList()) {
            database.execute("INSERT INTO placement_advertising_categories VALUES (?, ?)", placementId, category);
        }
    }
}
