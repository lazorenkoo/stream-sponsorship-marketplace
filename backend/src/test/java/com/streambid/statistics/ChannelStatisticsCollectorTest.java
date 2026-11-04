package com.streambid.statistics;

import com.streambid.channel.ChannelCredentials;
import com.streambid.channel.ChannelCredentialsRepository;
import com.streambid.channel.ConnectedChannel;
import com.streambid.platform.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChannelStatisticsCollectorTest {
    private final StreamingPlatformProvider provider = mock(StreamingPlatformProvider.class);
    private final ChannelCredentialsRepository credentials = mock(ChannelCredentialsRepository.class);
    private final ChannelMeasurementRepository measurements = mock(ChannelMeasurementRepository.class);
    private final Instant now = Instant.parse("2026-11-04T10:57:00Z");
    private final ConnectedChannel channel = new ConnectedChannel(UUID.randomUUID(), UUID.randomUUID(),
            StreamingPlatform.TWITCH, "opaque", "Streamer", now.atOffset(ZoneOffset.UTC));

    private ChannelStatisticsCollector collector() {
        when(provider.platform()).thenReturn(StreamingPlatform.TWITCH);
        when(credentials.find(channel.id())).thenReturn(Optional.of(mock(ChannelCredentials.class)));
        when(measurements.saveIfConnected(any())).thenReturn(true);
        return new ChannelStatisticsCollector(new PlatformRegistry(List.of(provider)), credentials,
                measurements, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void collectsLiveAndOfflineWithoutInventingViewers() {
        var collector = collector();
        for (var state : List.of(StreamState.LIVE, StreamState.OFFLINE)) {
            reset(measurements);
            when(measurements.saveIfConnected(any())).thenReturn(true);
            when(provider.getStreamStatus("opaque")).thenReturn(new StreamStatus("opaque", state,
                    state == StreamState.LIVE ? 42L : null, now));
            assertThat(collector.collect(channel)).isTrue();
            var captured = ArgumentCaptor.forClass(ChannelMeasurement.class);
            verify(measurements).saveIfConnected(captured.capture());
            assertThat(captured.getValue().state()).isEqualTo(state);
            assertThat(captured.getValue().measuredAt()).isEqualTo(now);
            assertThat(captured.getValue().viewers()).isEqualTo(state == StreamState.LIVE ? 42L : null);
        }
    }

    @Test
    void unavailableAndMissingChannelsAreUnknown() {
        var collector = collector();
        when(provider.getStreamStatus("opaque")).thenThrow(new PlatformUnavailableException(
                StreamingPlatform.TWITCH, null), new ChannelNotFoundException(StreamingPlatform.TWITCH));
        collector.collect(channel);
        collector.collect(channel);
        var captured = ArgumentCaptor.forClass(ChannelMeasurement.class);
        verify(measurements, times(2)).saveIfConnected(captured.capture());
        assertThat(captured.getAllValues()).allSatisfy(value -> {
            assertThat(value.state()).isEqualTo(StreamState.UNKNOWN);
            assertThat(value.viewers()).isNull();
        });
    }

    @Test
    void skipsDisconnectedAndUnsupportedChannels() {
        var collector = collector();
        when(credentials.find(channel.id())).thenReturn(Optional.empty());
        assertThat(collector.collect(channel)).isFalse();
        verify(provider, never()).getStreamStatus(any());
        verifyNoInteractions(measurements);
        var unsupported = new ChannelStatisticsCollector(new PlatformRegistry(List.of()), credentials,
                measurements, Clock.fixed(now, ZoneOffset.UTC));
        assertThat(unsupported.collect(channel)).isFalse();
    }

    @Test
    void mismatchedIdentityAndUnknownStatusCannotBecomeOffline() {
        var collector = collector();
        when(provider.getStreamStatus("opaque")).thenReturn(
                new StreamStatus("different", StreamState.LIVE, 99L, now),
                new StreamStatus("opaque", StreamState.UNKNOWN, null, now));
        collector.collect(channel);
        collector.collect(channel);
        var captured = ArgumentCaptor.forClass(ChannelMeasurement.class);
        verify(measurements, times(2)).saveIfConnected(captured.capture());
        assertThat(captured.getAllValues()).allSatisfy(value -> {
            assertThat(value.channelId()).isEqualTo(channel.id());
            assertThat(value.state()).isEqualTo(StreamState.UNKNOWN);
            assertThat(value.viewers()).isNull();
        });
    }
    @Test
    void respectsDisconnectionBeforeSavingMeasurement() {
        var collector = collector();
        when(provider.getStreamStatus("opaque")).thenReturn(new StreamStatus("opaque", StreamState.LIVE, 3L, now));
        when(measurements.saveIfConnected(any())).thenReturn(false);
        assertThat(collector.collect(channel)).isFalse();
    }
}
