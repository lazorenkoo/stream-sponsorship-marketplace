package com.streambid.campaign;

import com.streambid.platform.StreamingPlatform;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CampaignTargetingRepository {
    private final DSLContext database;

    public CampaignTargetingRepository(DSLContext database) {
        this.database = database;
    }

    public Optional<CampaignTargeting> readOwned(UUID campaignId, UUID owner) {
        var rows = database.fetch("""
                SELECT c.id, t.platform FROM campaigns c
                LEFT JOIN campaign_target_platforms t ON t.campaign_id = c.id
                WHERE c.id = ? AND c.advertiser_id = ? ORDER BY t.platform
                """, campaignId, owner);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var platforms = EnumSet.noneOf(StreamingPlatform.class);
        for (var row : rows) {
            String platform = row.get("platform", String.class);
            if (platform != null) {
                platforms.add(StreamingPlatform.valueOf(platform));
            }
        }
        return Optional.of(new CampaignTargeting(campaignId, platforms));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceInTransaction(CampaignTargeting targeting) {
        database.execute("DELETE FROM campaign_target_platforms WHERE campaign_id = ?", targeting.campaignId());
        for (var platform : new java.util.TreeSet<>(targeting.platforms())) {
            database.execute("INSERT INTO campaign_target_platforms (campaign_id, platform) VALUES (?, ?)",
                    targeting.campaignId(), platform.name());
        }
    }
}
