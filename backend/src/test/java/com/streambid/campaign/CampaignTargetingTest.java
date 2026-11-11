package com.streambid.campaign;

import com.streambid.platform.StreamingPlatform;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignTargetingTest {
    @Test
    void preservesExplicitSelectionAndDefensivelyCopiesPlatforms() {
        var selected = new HashSet<>(Set.of(StreamingPlatform.TWITCH));
        var targeting = new CampaignTargeting(UUID.randomUUID(), selected);
        selected.add(StreamingPlatform.KICK);
        assertThat(targeting.platforms()).containsExactly(StreamingPlatform.TWITCH);
        assertThatThrownBy(() -> targeting.platforms().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new CampaignTargeting(UUID.randomUUID(), Set.of()).platforms()).isEmpty();
    }

    @Test
    void rejectsMissingCampaignAndNullPlatforms() {
        assertThatThrownBy(() -> new CampaignTargeting(null, Set.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CampaignTargeting(UUID.randomUUID(), null)).isInstanceOf(NullPointerException.class);
        var selected = new HashSet<StreamingPlatform>();
        selected.add(null);
        assertThatThrownBy(() -> new CampaignTargeting(UUID.randomUUID(), selected)).isInstanceOf(NullPointerException.class);
    }
}
