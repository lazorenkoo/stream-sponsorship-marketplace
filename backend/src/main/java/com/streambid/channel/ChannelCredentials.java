package com.streambid.channel;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public record ChannelCredentials(UUID channelId, String accessTokenCiphertext, String refreshTokenCiphertext,
                                 OffsetDateTime expiresAt, OffsetDateTime lastValidatedAt) {

    public ChannelCredentials {
        expiresAt = expiresAt.withOffsetSameInstant(ZoneOffset.UTC);
        lastValidatedAt = lastValidatedAt.withOffsetSameInstant(ZoneOffset.UTC);
    }

    @Override
    public String toString() {
        return "ChannelCredentials[redacted]";
    }
}
