package com.streambid.statistics;

import com.streambid.channel.ChannelRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "statistics.enabled", havingValue = "true")
public class ChannelStatisticsJob {
    private static final Logger LOG = LoggerFactory.getLogger(ChannelStatisticsJob.class);
    private static final int BATCH_SIZE = 50;
    private final ChannelRepository channels;
    private final ChannelStatisticsCollector collector;

    public ChannelStatisticsJob(ChannelRepository channels, ChannelStatisticsCollector collector) {
        this.channels = channels;
        this.collector = collector;
    }

    @Scheduled(initialDelayString = "${statistics.interval-ms:60000}", fixedDelayString = "${statistics.interval-ms:60000}")
    public synchronized void collectConnected() {
        UUID after = null;
        while (true) {
            var batch = channels.connectedAfter(after, BATCH_SIZE);
            for (var channel : batch) {
                try {
                    collector.collect(channel);
                } catch (RuntimeException exception) {
                    LOG.warn("Statistics collection failed for channel {} ({})",
                            channel.id(), exception.getClass().getSimpleName());
                }
            }
            if (batch.size() < BATCH_SIZE) {
                return;
            }
            after = batch.getLast().id();
        }
    }
}
