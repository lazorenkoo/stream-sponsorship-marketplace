package com.streambid.statistics;

import com.streambid.platform.StreamState;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelMeasurementTest {
    private final UUID channel = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-04T09:50:00Z");

    private ChannelMeasurement measurement(StreamState state, Long viewers) {
        return new ChannelMeasurement(UUID.randomUUID(), channel, now, state, viewers);
    }

    @Test
    void preservesUnknownViewerCountAndOfflineState() {
        assertThat(measurement(StreamState.UNKNOWN, null).viewers()).isNull();
        assertThat(measurement(StreamState.OFFLINE, null).state()).isEqualTo(StreamState.OFFLINE);
        assertThat(measurement(StreamState.LIVE, 0L).viewers()).isZero();
        assertThat(measurement(StreamState.LIVE, null).viewers()).isNull();
    }

    @Test
    void rejectsInvalidViewerCounts() {
        assertThatThrownBy(() -> measurement(StreamState.LIVE, -1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> measurement(StreamState.OFFLINE, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> measurement(StreamState.UNKNOWN, 1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresMeasurementIdentityAndTime() {
        assertThatThrownBy(() -> new ChannelMeasurement(null, channel, now, StreamState.LIVE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ChannelMeasurement(UUID.randomUUID(), null, now, StreamState.LIVE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ChannelMeasurement(UUID.randomUUID(), channel, null, StreamState.LIVE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> measurement(null, null)).isInstanceOf(NullPointerException.class);
    }
}
