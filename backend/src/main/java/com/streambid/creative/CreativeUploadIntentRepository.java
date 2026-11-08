package com.streambid.creative;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class CreativeUploadIntentRepository {
    private final DSLContext database;

    public CreativeUploadIntentRepository(DSLContext database) {
        this.database = database;
    }

    public void create(UUID id, CreativeFormat format, Instant now) {
        database.execute("""
                INSERT INTO creative_upload_intents(creative_id, format, created_at, cleanup_after)
                VALUES (?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz) + INTERVAL '1 hour')
                """, id, format.name(), time(now), time(now));
    }

    public void complete(UUID id) {
        database.execute("DELETE FROM creative_upload_intents WHERE creative_id = ?", id);
    }

    public List<CreativeUploadIntent> claimExpired(Instant now, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Cleanup batch limit must be between 1 and 100");
        return database.fetch("""
                WITH due AS (
                    SELECT creative_id FROM creative_upload_intents WHERE cleanup_after <= CAST(? AS timestamptz)
                    ORDER BY cleanup_after, creative_id LIMIT ? FOR UPDATE SKIP LOCKED
                )
                UPDATE creative_upload_intents i SET attempts = attempts + 1,
                    cleanup_after = CAST(? AS timestamptz) + INTERVAL '5 minutes'
                FROM due WHERE i.creative_id = due.creative_id RETURNING i.*
                """, time(now), limit, time(now)).map(row -> new CreativeUploadIntent(row.get("creative_id", UUID.class),
                        CreativeFormat.valueOf(row.get("format", String.class)), row.get("attempts", Integer.class)));
    }

    private OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
