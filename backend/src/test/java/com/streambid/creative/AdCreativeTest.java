package com.streambid.creative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdCreativeTest {
    @Test
    void storageKeyUsesServerIdentityAndDimensionsAreBounded() {
        UUID id = UUID.randomUUID();
        var creative = creative(id, 320, 180);
        assertThat(creative.storageKey()).isEqualTo("creatives/" + id + ".png");
        assertThat(creative.status()).isEqualTo(CreativeStatus.PENDING);
        assertThatThrownBy(() -> creative(id, 8192, 8192)).isInstanceOf(IllegalArgumentException.class);
    }

    private AdCreative creative(UUID id, int width, int height) {
        return new AdCreative(id, UUID.randomUUID(), "Banner", CreativeFormat.PNG, width, height, 100,
                "a".repeat(64), CreativeStatus.PENDING, 0, Instant.now());
    }
}
