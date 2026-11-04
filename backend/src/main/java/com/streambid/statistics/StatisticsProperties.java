package com.streambid.statistics;

import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

@Validated
public class StatisticsProperties {
    @Min(10000)
    private long intervalMs = 60000;

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }
}
