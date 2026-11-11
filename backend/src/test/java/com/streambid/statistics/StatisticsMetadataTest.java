package com.streambid.statistics;

import com.streambid.channel.*;
import com.streambid.platform.*;
import com.streambid.user.UserAccount;
import com.streambid.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StatisticsMetadataTest {
    @Test
    void eligibilityRequiresEnabledCollectorProviderAndCredentials() {
        var channels = mock(ChannelRepository.class);
        var users = mock(UserRepository.class);
        var credentials = mock(ChannelCredentialsRepository.class);
        var measurements = mock(ChannelMeasurementRepository.class);
        var account = new UserAccount(UUID.randomUUID(), "owner@example.com", "hash", "Owner");
        var channel = new ConnectedChannel(UUID.randomUUID(), account.id(), StreamingPlatform.TWITCH,
                "opaque", "Streamer", OffsetDateTime.now());
        when(users.findByEmail(account.email())).thenReturn(Optional.of(account));
        when(channels.find(channel.id())).thenReturn(Optional.of(channel));
        var provider = mock(StreamingPlatformProvider.class);
        when(provider.platform()).thenReturn(StreamingPlatform.TWITCH);
        for (boolean enabled : new boolean[] {false, true}) {
            for (boolean available : new boolean[] {false, true}) {
                for (boolean authorized : new boolean[] {false, true}) {
                    when(credentials.find(channel.id())).thenReturn(authorized
                            ? Optional.of(mock(ChannelCredentials.class)) : Optional.empty());
                    var controller = new ChannelMeasurementController(channels, users, measurements,
                            new StatisticsProperties(), new StatisticsRetentionProperties(false, 90), credentials,
                            new PlatformRegistry(available ? List.of(provider) : List.of()), enabled);
                    var metadata = controller.metadata(channel.id(), account::email).getBody();
                    assertThat(metadata.collectionEnabled()).isEqualTo(enabled);
                    assertThat(metadata.providerAvailable()).isEqualTo(available);
                    assertThat(metadata.authorizationRequired()).isEqualTo(!authorized);
                    assertThat(metadata.collectionEligible()).isEqualTo(enabled && available && authorized);
                }
            }
        }
    }
}
