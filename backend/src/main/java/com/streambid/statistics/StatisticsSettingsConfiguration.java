package com.streambid.statistics;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StatisticsRetentionProperties.class)
public class StatisticsSettingsConfiguration {
    @Bean
    @ConfigurationProperties("statistics")
    public StatisticsProperties statisticsProperties() {
        return new StatisticsProperties();
    }
}
