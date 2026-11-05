package com.streambid.campaign;

import com.streambid.platform.StreamingPlatform;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class CampaignCandidateRepository {
    private final DSLContext database;

    public CampaignCandidateRepository(DSLContext database) {
        this.database = database;
    }

    public List<CampaignCandidate> find(Set<StreamingPlatform> selected, UUID after, int limit) {
        var platforms = Set.copyOf(selected);
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Candidate limit must be between 1 and 100");
        }
        if (platforms.isEmpty()) {
            return List.of();
        }
        var id = DSL.field(DSL.name("c", "id"), UUID.class);
        var platform = DSL.field(DSL.name("c", "platform"), String.class);
        var name = DSL.field(DSL.name("c", "display_name"), String.class);
        return database.select(id, platform, name).from(DSL.table(DSL.name("connected_channels")).as("c"))
                .join(DSL.table(DSL.name("channel_listings")).as("l"))
                .on(id.eq(DSL.field(DSL.name("l", "channel_id"), UUID.class)))
                .join(DSL.table(DSL.name("channel_credentials")).as("t"))
                .on(id.eq(DSL.field(DSL.name("t", "channel_id"), UUID.class)))
                .where(DSL.field(DSL.name("l", "listed"), Boolean.class).isTrue())
                .and(platform.in(platforms.stream().map(Enum::name).toList()))
                .and(after == null ? DSL.trueCondition() : id.gt(after)).orderBy(id).limit(limit)
                .fetch(row -> new CampaignCandidate(row.get(id), StreamingPlatform.valueOf(row.get(platform)), row.get(name)));
    }
}
