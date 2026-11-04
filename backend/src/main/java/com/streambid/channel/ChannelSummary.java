package com.streambid.channel;

import com.streambid.platform.StreamingPlatform;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public record ChannelSummary(UUID id, StreamingPlatform platform, String externalChannelId, String displayName,
                             OffsetDateTime verifiedAt, OffsetDateTime lastValidatedAt, boolean authorizationRequired) {

    public ChannelSummary {
        verifiedAt = verifiedAt.withOffsetSameInstant(ZoneOffset.UTC);
        if (lastValidatedAt != null) {
            lastValidatedAt = lastValidatedAt.withOffsetSameInstant(ZoneOffset.UTC);
        }
    }
}
