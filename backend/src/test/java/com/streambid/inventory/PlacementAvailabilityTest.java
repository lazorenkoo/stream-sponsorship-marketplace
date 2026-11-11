package com.streambid.inventory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlacementAvailabilityTest {
    @Test
    void rejectsIntervalsThatCollapseAtDatabasePrecision() {
        var start = Instant.parse("2026-11-07T10:00:00Z");
        assertThatThrownBy(() -> new PlacementAvailability(UUID.randomUUID(), UUID.randomUUID(), start, start))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlacementAvailability(UUID.randomUUID(), UUID.randomUUID(), start, start.plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
