package com.streambid.inventory;

import java.time.Instant;

public record PlacementHours(Instant startsAt, Instant endsAt) { }
