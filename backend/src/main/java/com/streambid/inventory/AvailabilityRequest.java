package com.streambid.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record AvailabilityRequest(@NotNull Instant startsAt, @NotNull Instant endsAt,
        @NotNull @PositiveOrZero Long expectedVersion) { }
