package com.streambid.booking;
import java.time.Instant;
public record BookingCancellationView(long version, String actorRole, String reason, Instant cancelledAt) { }
