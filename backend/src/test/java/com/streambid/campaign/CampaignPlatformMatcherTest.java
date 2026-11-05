package com.streambid.campaign;

import com.streambid.platform.StreamingPlatform;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignPlatformMatcherTest {
    @ParameterizedTest
    @EnumSource(StreamingPlatform.class)
    void matchesOnlyExplicitlySelectedPlatforms(StreamingPlatform selected) {
        var targeting = new CampaignTargeting(UUID.randomUUID(), Set.of(selected));
        for (var candidate : StreamingPlatform.values()) {
            assertThat(CampaignPlatformMatcher.evaluate(targeting, candidate, true)).isEqualTo(
                    candidate == selected ? CampaignPlatformMatch.MATCHED : CampaignPlatformMatch.PLATFORM_NOT_SELECTED);
        }
    }

    @Test
    void emptySelectionDoesNotMatchEveryPlatform() {
        var targeting = new CampaignTargeting(UUID.randomUUID(), Set.of());
        assertThat(CampaignPlatformMatcher.evaluate(targeting, StreamingPlatform.TWITCH, true))
                .isEqualTo(CampaignPlatformMatch.NO_PLATFORMS_SELECTED);
    }

    @Test
    void unauthorizedChannelsNeverMatchEvenWhenSelected() {
        for (var platform : StreamingPlatform.values()) {
            var targeting = new CampaignTargeting(UUID.randomUUID(), Set.of(platform));
            assertThat(CampaignPlatformMatcher.evaluate(targeting, platform, false))
                    .isEqualTo(CampaignPlatformMatch.AUTHORIZATION_REQUIRED);
        }
    }

    @Test
    void requiresTargetingAndChannelPlatform() {
        var targeting = new CampaignTargeting(UUID.randomUUID(), Set.of(StreamingPlatform.TWITCH));
        assertThatThrownBy(() -> CampaignPlatformMatcher.evaluate(null, StreamingPlatform.TWITCH, true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CampaignPlatformMatcher.evaluate(targeting, null, true))
                .isInstanceOf(NullPointerException.class);
    }
}
