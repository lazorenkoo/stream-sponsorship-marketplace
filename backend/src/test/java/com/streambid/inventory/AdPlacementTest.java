package com.streambid.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdPlacementTest {
    @Test
    void preservesExactPriceAndRequiresPriceOrNegotiation() {
        assertThat(placement(new BigDecimal("200"), false).minimumPrice()).isEqualByComparingTo("200.00");
        assertThat(placement(null, true).minimumPrice()).isNull();
        assertThatThrownBy(() -> placement(null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> placement(new BigDecimal("0.001"), false)).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> placement(new BigDecimal("-1"), false)).isInstanceOf(IllegalArgumentException.class);
    }

    private AdPlacement placement(BigDecimal price, boolean negotiable) {
        var now = Instant.parse("2026-11-06T12:00:00Z");
        return new AdPlacement(UUID.randomUUID(), UUID.randomUUID(), "Banner", "Top right", 320, 180,
                3600, price, "PLN", negotiable, 1, false, 0, now, now);
    }
}
