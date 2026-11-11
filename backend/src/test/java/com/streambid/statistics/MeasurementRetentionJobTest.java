package com.streambid.statistics;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MeasurementRetentionJobTest {
    private final ChannelMeasurementRepository measurements = mock(ChannelMeasurementRepository.class);
    private final Instant now = Instant.parse("2026-11-04T15:06:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @Test
    void usesFixedCutoffAndStopsAfterShortBatch() {
        var cutoff = now.minus(90, ChronoUnit.DAYS);
        when(measurements.deleteBefore(cutoff, 1000)).thenReturn(1000, 2);
        new MeasurementRetentionJob(measurements, new StatisticsRetentionProperties(true, 90), clock).cleanExpired();
        verify(measurements, times(2)).deleteBefore(cutoff, 1000);
        verifyNoMoreInteractions(measurements);
    }

    @Test
    void limitsEachRunToTenBatches() {
        var cutoff = now.minus(30, ChronoUnit.DAYS);
        when(measurements.deleteBefore(cutoff, 1000)).thenReturn(1000);
        new MeasurementRetentionJob(measurements, new StatisticsRetentionProperties(true, 30), clock).cleanExpired();
        verify(measurements, times(10)).deleteBefore(cutoff, 1000);
        verifyNoMoreInteractions(measurements);
    }

    @Test
    void disabledJobNeverDeletesMeasurements() {
        new MeasurementRetentionJob(measurements, new StatisticsRetentionProperties(false, 90), clock).cleanExpired();
        verifyNoInteractions(measurements);
    }

    @Test
    void configurationRequiresExplicitEnablement() {
        var runner = new ApplicationContextRunner()
                .withBean(ChannelMeasurementRepository.class, () -> measurements)
                .withUserConfiguration(StatisticsSettingsConfiguration.class, MeasurementRetentionConfiguration.class);
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(MeasurementRetentionJob.class));
        runner.withPropertyValues("statistics.retention.enabled=true", "statistics.retention.days=30").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(MeasurementRetentionJob.class);
            assertThat(context.getBean(StatisticsRetentionProperties.class).days()).isEqualTo(30);
            verifyNoInteractions(measurements);
        });
    }
}
