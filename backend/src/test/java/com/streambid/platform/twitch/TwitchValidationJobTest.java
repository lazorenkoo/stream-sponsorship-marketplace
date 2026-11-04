package com.streambid.platform.twitch;

import com.streambid.channel.ChannelCredentialsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TwitchValidationJobTest {

    private final ChannelCredentialsRepository credentials = mock(ChannelCredentialsRepository.class);
    private final TwitchValidationCycle cycle = mock(TwitchValidationCycle.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T08:00:00Z"), ZoneOffset.UTC);
    private final OffsetDateTime now = OffsetDateTime.now(clock);
    private final TwitchValidationJob job = new TwitchValidationJob(credentials, cycle, clock);

    @Test
    void startupScansMultipleBatchesAndIsolatesChannelFailures() {
        var first = java.util.stream.IntStream.rangeClosed(1, 50).mapToObj(i -> new UUID(0, i)).toList();
        UUID last = new UUID(0, 51);
        when(credentials.dueForValidation(now, null, 50)).thenReturn(first);
        when(credentials.dueForValidation(now, first.getLast(), 50)).thenReturn(List.of(last));
        when(credentials.dueForValidation(now.minusMinutes(55), null, 50)).thenReturn(List.of());
        when(cycle.check(first.getFirst())).thenThrow(new IllegalStateException("Sensitive error must not be logged"));
        assertThatCode(job::validateDue).doesNotThrowAnyException();
        verify(cycle, times(51)).check(any());
        verify(cycle).check(last);
        job.validateDue();
        verify(credentials).dueForValidation(now.minusMinutes(55), null, 50);
    }

    @Test
    void failedStartupScanIsRetriedAsStartup() {
        when(credentials.dueForValidation(now, null, 50))
                .thenThrow(new IllegalStateException("Database temporarily unavailable")).thenReturn(List.of());
        assertThatThrownBy(job::validateDue).isInstanceOf(IllegalStateException.class);
        assertThatCode(job::validateDue).doesNotThrowAnyException();
        verify(credentials, times(2)).dueForValidation(now, null, 50);
    }
}
