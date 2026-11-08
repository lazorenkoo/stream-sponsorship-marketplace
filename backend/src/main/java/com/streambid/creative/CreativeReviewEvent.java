package com.streambid.creative;
import java.time.Instant;
public record CreativeReviewEvent(long version, CreativeStatus status, String reason, Instant occurredAt) { }
