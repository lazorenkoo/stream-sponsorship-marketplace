package com.streambid.channel;

import com.streambid.platform.StreamingPlatform;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public record ConnectedChannel(UUID id, UUID userId, StreamingPlatform platform,
                               String externalChannelId, String displayName, OffsetDateTime verifiedAt) {

    public ConnectedChannel {
        verifiedAt = verifiedAt.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
