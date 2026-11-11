package com.streambid.security;

import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCipherTest {

    private final String key = Base64.getEncoder().encodeToString(new byte[32]);
    private final TokenCipher cipher = new TokenCipher(key);

    @Test
    void roundTripsWithoutExposingTokenAndUsesFreshNonces() {
        String first = cipher.encrypt("private-token", "channel:123:access");
        String second = cipher.encrypt("private-token", "channel:123:access");
        assertThat(first).startsWith("v1.").doesNotContain("private-token").isNotEqualTo(second);
        assertThat(cipher.decrypt(first, "channel:123:access")).isEqualTo("private-token");
    }

    @Test
    void rejectsTamperingAndWrongContext() {
        String encrypted = cipher.encrypt("private-token", "channel:123:access");
        byte[] payload = Base64.getDecoder().decode(encrypted.substring(3));
        payload[payload.length - 1] ^= 1;
        String tampered = "v1." + Base64.getEncoder().encodeToString(payload);
        assertThatThrownBy(() -> cipher.decrypt(tampered, "channel:123:access"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cipher.decrypt(encrypted, "channel:456:access"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWrongKeyAndMissingConfiguration() {
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        var otherCipher = new TokenCipher(Base64.getEncoder().encodeToString(otherKey));
        String encrypted = cipher.encrypt("private-token", "channel:123:access");
        assertThatThrownBy(() -> otherCipher.decrypt(encrypted, "channel:123:access"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenCipher("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cipher.encrypt("x".repeat(16385), "context"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
