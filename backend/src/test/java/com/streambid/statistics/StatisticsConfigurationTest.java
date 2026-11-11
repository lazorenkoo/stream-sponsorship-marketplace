package com.streambid.statistics;

import com.streambid.channel.ChannelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StatisticsConfigurationTest {
    private final ChannelRepository channels = mock(ChannelRepository.class);
    private final ChannelStatisticsCollector collector = mock(ChannelStatisticsCollector.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withBean(ChannelRepository.class, () -> channels)
            .withBean(ChannelStatisticsCollector.class, () -> collector)
            .withUserConfiguration(StatisticsSettingsConfiguration.class, StatisticsConfiguration.class, ChannelStatisticsJob.class);

    @Test
    void enabledConfigurationBindsIntervalWithoutStartingCollectionImmediately() {
        runner.withPropertyValues("statistics.enabled=true", "statistics.interval-ms=120000")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ChannelStatisticsJob.class);
                    assertThat(context.getBean(StatisticsProperties.class).getIntervalMs()).isEqualTo(120000);
                    verifyNoInteractions(channels, collector);
                });
    }

    @Test
    void enabledConfigurationUsesDefaultInterval() {
        runner.withPropertyValues("statistics.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(StatisticsProperties.class).getIntervalMs()).isEqualTo(60000);
        });
    }

    @Test
    void rejectsIntervalsBelowTenSeconds() {
        runner.withPropertyValues("statistics.enabled=true", "statistics.interval-ms=9999")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void disabledConfigurationHasNoJob() {
        runner.withPropertyValues("statistics.enabled=false").run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(ChannelStatisticsJob.class)
                        .hasSingleBean(StatisticsProperties.class));
    }
}
