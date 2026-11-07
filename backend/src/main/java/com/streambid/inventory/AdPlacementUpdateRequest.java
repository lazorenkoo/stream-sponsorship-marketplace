package com.streambid.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AdPlacementUpdateRequest(@NotNull @Valid AdPlacementRequest placement,
        @NotNull @PositiveOrZero Long expectedVersion) { }
