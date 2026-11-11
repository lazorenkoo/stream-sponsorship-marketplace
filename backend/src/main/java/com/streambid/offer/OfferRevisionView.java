package com.streambid.offer;

import java.time.Instant;
public record OfferRevisionView(long revision, String proposedByRole, OfferTerms terms, Instant createdAt) { }
