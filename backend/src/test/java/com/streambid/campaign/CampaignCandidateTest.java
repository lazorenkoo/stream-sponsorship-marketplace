package com.streambid.campaign;

import com.streambid.platform.StreamingPlatform;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignCandidateTest {
    @Test
    void validatesCandidateFieldsAndDatabaseNameLength() {
        var id = UUID.randomUUID();
        assertThat(new CampaignCandidate(id, StreamingPlatform.TWITCH, "Streamer").displayName())
                .isEqualTo("Streamer");
        assertThat(new CampaignCandidate(id, StreamingPlatform.TWITCH, "N".repeat(255)).displayName()).hasSize(255);
        assertThatThrownBy(() -> new CampaignCandidate(id, StreamingPlatform.TWITCH, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CampaignCandidate(id, StreamingPlatform.TWITCH, "N".repeat(256)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CampaignCandidate(null, StreamingPlatform.TWITCH, "Streamer"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void previewRequiresVersionAndCopiesCandidateList() {
        var campaignId = UUID.randomUUID();
        var candidate = new CampaignCandidate(UUID.randomUUID(), StreamingPlatform.TWITCH, "Streamer");
        var mutable = new ArrayList<>(java.util.List.of(candidate));
        var preview = new CampaignCandidatePreview(campaignId, 0, mutable, null, false);
        mutable.clear();
        assertThat(preview.candidates()).containsExactly(candidate);
        assertThatThrownBy(() -> new CampaignCandidatePreview(null, 0, java.util.List.of(), null, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CampaignCandidatePreview(campaignId, -1, java.util.List.of(), null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CampaignCandidatePreview(campaignId, 0, java.util.List.of(), null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
