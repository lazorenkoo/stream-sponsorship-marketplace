package com.streambid.statistics;

import java.time.Instant;

public record MeasurementAvailability(Instant firstMeasuredAt, Instant lastMeasuredAt) {
}
