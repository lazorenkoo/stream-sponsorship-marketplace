package com.streambid.offer;

import java.time.Instant;
public record OfferEventView(long version, long revision, OfferStatus status, String actorRole, Instant occurredAt) { }
