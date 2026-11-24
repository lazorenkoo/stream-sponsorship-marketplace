package com.streambid.offer;

import java.time.Instant;
import java.util.UUID;

public record OfferView(UUID id, UUID placementId, OfferStatus status, long version, long revision,
        OfferTerms terms, Instant createdAt, Instant updatedAt, boolean recipient, boolean advertiser, UUID campaignId, UUID channelId, String channelName, String placementTitle, UUID bookingId) { }
