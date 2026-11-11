package com.streambid.statistics;

import java.math.BigDecimal;
import java.time.Instant;

public record HourlyStatistics(Instant hour, long totalSamples, long liveSamples, long unknownSamples,
        long knownViewerSamples, BigDecimal averageViewers, Long peakViewers) {
}
