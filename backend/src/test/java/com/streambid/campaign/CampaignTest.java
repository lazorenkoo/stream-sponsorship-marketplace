package com.streambid.campaign;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignTest {
    private final Instant now = Instant.parse("2026-11-04T16:24:00.123456789Z");

    private Campaign draft(String title, String description, Instant updatedAt) {
        return new Campaign(UUID.randomUUID(), UUID.randomUUID(), title, description,
                CampaignStatus.DRAFT, now, updatedAt);
    }

    @Test
    void normalizesTitleAndDatabaseTimePrecision() {
        var campaign = draft("  First campaign  ", "Multiline\nbrief", now);
        assertThat(campaign.title()).isEqualTo("First campaign");
        assertThat(campaign.description()).isEqualTo("Multiline\nbrief");
        assertThat(campaign.status()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(campaign.createdAt()).isEqualTo(Instant.parse("2026-11-04T16:24:00.123456Z"));
        assertThat(campaign.updatedAt()).isEqualTo(campaign.createdAt());
        assertThat(draft("A".repeat(120), "", now).title()).hasSize(120);
    }

    @Test
    void rejectsInvalidTitlesAndDescriptions() {
        for (String title : new String[] {" ", "A".repeat(121), "Line\nbreak"}) {
            assertThatThrownBy(() -> draft(title, "", now)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> draft("Title", "x".repeat(5001), now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> draft("Title", "null\0character", now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> draft("Title", "", now.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
