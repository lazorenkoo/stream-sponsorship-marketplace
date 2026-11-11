package com.streambid.statistics;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("statistics.retention")
public record StatisticsRetentionProperties(@DefaultValue("false") boolean enabled, @DefaultValue("90") int days) {
    public StatisticsRetentionProperties {
        if (days < 1 || days > 3650) {
            throw new IllegalArgumentException("Retention days must be between 1 and 3650");
        }
    }
}
