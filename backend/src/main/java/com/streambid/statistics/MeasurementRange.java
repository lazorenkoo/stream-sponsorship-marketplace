package com.streambid.statistics;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record MeasurementRange(Instant from, Instant to) {
    public MeasurementRange {
        if (from == null || to == null || !from.isBefore(to)
                || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0) {
            throw new IllegalArgumentException("Statistics range must be positive and at most 31 days");
        }
        from = from.truncatedTo(ChronoUnit.MICROS);
        to = to.truncatedTo(ChronoUnit.MICROS);
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("Statistics range must span at least one microsecond");
        }
    }
}
