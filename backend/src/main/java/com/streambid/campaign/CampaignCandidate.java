package com.streambid.campaign;

import com.streambid.platform.StreamingPlatform;
import java.util.UUID;

public record CampaignCandidate(UUID channelId, StreamingPlatform platform, String displayName) {
}
