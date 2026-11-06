package com.streambid.campaign;

import java.time.Instant;
import java.util.UUID;

public record PublishedCampaign(UUID id, String title, String description, Instant publishedAt,
        java.util.List<com.streambid.platform.StreamingPlatform> platforms) {
    public PublishedCampaign {
        platforms = java.util.List.copyOf(platforms);
    }
}
