package com.streambid.statistics;

public record StatisticsMetadata(MeasurementAvailability availability, boolean collectionEnabled,
        long collectionIntervalMs, boolean retentionEnabled, Integer retentionDays, int maxRangeDays) {
}
