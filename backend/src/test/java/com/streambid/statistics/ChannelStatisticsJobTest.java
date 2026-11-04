package com.streambid.statistics;

import com.streambid.channel.ChannelRepository;
import com.streambid.channel.ConnectedChannel;
import com.streambid.platform.StreamingPlatform;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class ChannelStatisticsJobTest {
    private final ChannelRepository channels = mock(ChannelRepository.class);
    private final ChannelStatisticsCollector collector = mock(ChannelStatisticsCollector.class);
    private final ChannelStatisticsJob job = new ChannelStatisticsJob(channels, collector);

    private ConnectedChannel channel(int index) {
        return new ConnectedChannel(new UUID(0, index), UUID.randomUUID(), StreamingPlatform.TWITCH,
                "channel-" + index, "Streamer", OffsetDateTime.now());
    }

    @Test
    void collectsEveryPageAndContinuesAfterIndividualFailure() {
        var batch = IntStream.rangeClosed(1, 50).mapToObj(this::channel).toList();
        var last = channel(51);
        when(channels.connectedAfter(null, 50)).thenReturn(batch);
        when(channels.connectedAfter(batch.getLast().id(), 50)).thenReturn(List.of(last));
        when(collector.collect(batch.getFirst())).thenThrow(new IllegalStateException("Temporary failure"));
        job.collectConnected();
        for (var channel : batch) {
            verify(collector).collect(channel);
        }
        verify(collector).collect(last);
        verify(channels, times(2)).connectedAfter(any(), eq(50));
        verifyNoMoreInteractions(collector);
    }

    @Test
    void emptySelectionDoesNotCallProviders() {
        when(channels.connectedAfter(null, 50)).thenReturn(List.of());
        job.collectConnected();
        verifyNoInteractions(collector);
        verify(channels).connectedAfter(null, 50);
        verifyNoMoreInteractions(channels);
    }
}
