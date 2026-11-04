package com.streambid.platform.twitch;

import com.streambid.channel.ChannelCredentials;
import com.streambid.channel.ChannelCredentialsRepository;
import com.streambid.channel.ChannelRepository;
import com.streambid.channel.ConnectedChannel;
import com.streambid.platform.StreamingPlatform;
import com.streambid.security.TokenCipher;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TwitchValidationCycleTest {

    private final ChannelRepository channels = mock(ChannelRepository.class);
    private final ChannelCredentialsRepository credentials = mock(ChannelCredentialsRepository.class);
    private final TokenCipher cipher = mock(TokenCipher.class);
    private final TwitchTokenValidator validator = mock(TwitchTokenValidator.class);
    private final TwitchCredentialRefresh refresh = mock(TwitchCredentialRefresh.class);
    private final Instant now = Instant.parse("2026-11-04T07:00:00Z");
    private final OffsetDateTime time = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
    private final UUID id = UUID.randomUUID();
    private final ChannelCredentials snapshot = new ChannelCredentials(id, "encrypted", "refresh", time.plusHours(1), time.minusHours(1));
    private final TwitchValidationCycle cycle = new TwitchValidationCycle(channels, credentials, cipher,
            validator, refresh, Clock.fixed(now, ZoneOffset.UTC));

    @BeforeEach
    void setup() {
        when(channels.find(id)).thenReturn(Optional.of(new ConnectedChannel(id, UUID.randomUUID(),
                StreamingPlatform.TWITCH, "verified-user", "Streamer", time)));
        when(credentials.find(id)).thenReturn(Optional.of(snapshot));
        when(cipher.decrypt("encrypted", id + ":access")).thenReturn("access");
        when(validator.validate("access", "verified-user")).thenReturn(now.plusSeconds(3600));
        when(credentials.markValidated(any(), any(), any())).thenReturn(true);
    }

    @Test
    void recordsValidationOnlyForCurrentSnapshot() {
        assertThat(cycle.check(id)).isTrue();
        verify(credentials).markValidated(snapshot, time, time.plusHours(1));
        verifyNoInteractions(refresh);
        verify(credentials, never()).deleteIfUnchanged(any());
    }

    @Test
    void rejectedTokenRefreshesAndValidatesNewSnapshotOnce() {
        var rotated = new ChannelCredentials(id, "rotated", "rotated-refresh", time.plusHours(1), time.minusHours(1));
        when(credentials.find(id)).thenReturn(Optional.of(snapshot), Optional.of(rotated));
        when(validator.validate("access", "verified-user")).thenThrow(new TwitchAccessTokenRejectedException());
        when(cipher.decrypt("rotated", id + ":access")).thenReturn("new-access");
        when(validator.validate("new-access", "verified-user")).thenReturn(now.plusSeconds(3600));
        assertThat(cycle.check(id)).isTrue();
        verify(refresh, times(1)).refreshRejected(id, "access");
        verify(credentials).markValidated(rotated, time, time.plusHours(1));
    }
}
