package com.streambid.inventory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PlacementActivationRequest(@NotNull Boolean active, @NotNull @PositiveOrZero Long expectedVersion) { }
