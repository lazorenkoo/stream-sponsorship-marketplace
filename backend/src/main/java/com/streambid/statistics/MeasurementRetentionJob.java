package com.streambid.statistics;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;

public class MeasurementRetentionJob {
    private final ChannelMeasurementRepository measurements;
    private final StatisticsRetentionProperties properties;
    private final Clock clock;

    public MeasurementRetentionJob(ChannelMeasurementRepository measurements,
            StatisticsRetentionProperties properties, Clock clock) {
        this.measurements = measurements;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 3600000, fixedDelay = 3600000)
    public synchronized void cleanExpired() {
        if (!properties.enabled()) {
            return;
        }
        var cutoff = clock.instant().minus(properties.days(), ChronoUnit.DAYS);
        for (int batch = 0; batch < 10; batch++) {
            if (measurements.deleteBefore(cutoff, 1000) < 1000) {
                return;
            }
        }
    }
}
