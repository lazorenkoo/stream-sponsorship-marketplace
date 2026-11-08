package com.streambid.offer;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfferTermsTest {
    @Test
    void preservesExactMoneyAndRequiresStartOnlyForScheduledTerms() {
        UUID creative = UUID.randomUUID();
        var start = Instant.parse("2026-11-09T12:00:00.123456789Z");
        var terms = new OfferTerms(creative, new BigDecimal("350"), "PLN", 5400, OfferMode.SCHEDULED, start, "gaming");
        assertThat(terms.price()).isEqualByComparingTo("350.00");
        assertThat(terms.startsAt().getNano()).isEqualTo(123456000);
        assertThatThrownBy(() -> new OfferTerms(creative, new BigDecimal("0.001"), "PLN", 60,
                OfferMode.FLEXIBLE, null, "gaming")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OfferTerms(creative, BigDecimal.ONE, "PLN", 60,
                OfferMode.FLEXIBLE, start, "gaming")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OfferTerms(creative, BigDecimal.ONE, "PLN", 60,
                OfferMode.SCHEDULED, null, "gaming")).isInstanceOf(IllegalArgumentException.class);
    }
}
