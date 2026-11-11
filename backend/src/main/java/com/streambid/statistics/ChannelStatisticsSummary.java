package com.streambid.statistics;

import java.math.BigDecimal;
import java.time.Instant;

public record ChannelStatisticsSummary(Instant from, Instant to, long totalSamples, long liveSamples,
        long offlineSamples, long unknownSamples, long knownViewerSamples, BigDecimal averageViewers, Long peakViewers) {
}
