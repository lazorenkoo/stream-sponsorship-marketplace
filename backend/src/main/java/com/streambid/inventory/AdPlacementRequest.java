package com.streambid.inventory;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record AdPlacementRequest(@NotNull UUID channelId, @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 120) String position, @Min(1) @Max(7680) int widthPx,
        @Min(1) @Max(4320) int heightPx, @Min(1) @Max(604800) int durationSeconds,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal minimumPrice,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency, boolean negotiable,
        @Min(1) @Max(100) int capacity) { }
