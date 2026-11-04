package com.streambid.statistics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "statistics.enabled", havingValue = "true")
public class StatisticsConfiguration {
    @org.springframework.context.annotation.Bean
    @org.springframework.boot.context.properties.ConfigurationProperties("statistics")
    public StatisticsProperties statisticsProperties() {
        return new StatisticsProperties();
    }
}
