package com.streambid.platform.twitch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TwitchConfigurationTest {

    @Test
    void createsEnabledIntegrationWithoutMakingNetworkRequests() {
        new ApplicationContextRunner()
                .withUserConfiguration(TwitchClientConfiguration.class, TwitchTokenService.class, TwitchProvider.class)
                .withPropertyValues("twitch.enabled=true", "twitch.client-id=test", "twitch.client-secret=test",
                        "twitch.oauth.redirect-uri=http://localhost:8080/callback", "twitch.api-url=https://example.test/helix", "twitch.token-url=https://example.test/token")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(TwitchProvider.class)
                        .hasSingleBean(TwitchTokenService.class));
    }
}
