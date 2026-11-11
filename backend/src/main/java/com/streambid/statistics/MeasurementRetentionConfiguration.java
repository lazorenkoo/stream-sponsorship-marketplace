package com.streambid.statistics;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling

@ConditionalOnProperty(name = "statistics.retention.enabled", havingValue = "true")
public class MeasurementRetentionConfiguration {
    @Bean
    public MeasurementRetentionJob measurementRetentionJob(ChannelMeasurementRepository measurements,
            StatisticsRetentionProperties properties) {
        return new MeasurementRetentionJob(measurements, properties, Clock.systemUTC());
    }
}
