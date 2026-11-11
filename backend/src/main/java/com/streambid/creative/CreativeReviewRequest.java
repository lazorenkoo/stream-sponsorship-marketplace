package com.streambid.creative;
import jakarta.validation.constraints.*;

public record CreativeReviewRequest(@NotNull CreativeStatus status, @Size(max = 500) String reason,
        @NotNull @PositiveOrZero Long expectedVersion) { }
