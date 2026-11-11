package com.streambid.statistics;

import com.streambid.channel.ChannelCredentialsRepository;
import com.streambid.channel.ConnectedChannel;
import com.streambid.platform.ChannelNotFoundException;
import com.streambid.platform.PlatformRegistry;
import com.streambid.platform.PlatformUnavailableException;
import com.streambid.platform.StreamState;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChannelStatisticsCollector {
    private final PlatformRegistry platforms;
    private final ChannelCredentialsRepository credentials;
    private final ChannelMeasurementRepository measurements;
    private final Clock clock;

    @Autowired
    public ChannelStatisticsCollector(PlatformRegistry platforms, ChannelCredentialsRepository credentials,
                                      ChannelMeasurementRepository measurements) {
        this(platforms, credentials, measurements, Clock.systemUTC());
    }

    ChannelStatisticsCollector(PlatformRegistry platforms, ChannelCredentialsRepository credentials,
                               ChannelMeasurementRepository measurements, Clock clock) {
        this.platforms = platforms;
        this.credentials = credentials;
        this.measurements = measurements;
        this.clock = clock;
    }

    public boolean collect(ConnectedChannel channel) {
        var provider = platforms.find(channel.platform());
        if (provider.isEmpty() || credentials.find(channel.id()).isEmpty()) {
            return false;
        }
        StreamState state;
        Long viewers;
        try {
            var status = provider.get().getStreamStatus(channel.externalChannelId());
            state = status.channelId().equals(channel.externalChannelId()) ? status.state() : StreamState.UNKNOWN;
            viewers = state == StreamState.LIVE ? status.viewers() : null;
        } catch (PlatformUnavailableException | ChannelNotFoundException exception) {
            state = StreamState.UNKNOWN;
            viewers = null;
        }
        return measurements.saveIfConnected(new ChannelMeasurement(UUID.randomUUID(), channel.id(),
                clock.instant(), state, viewers));
    }
}
