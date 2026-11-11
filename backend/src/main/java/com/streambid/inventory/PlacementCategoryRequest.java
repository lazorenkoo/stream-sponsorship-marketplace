package com.streambid.inventory;
import jakarta.validation.constraints.*;
import java.util.Set;

public record PlacementCategoryRequest(@NotNull Boolean unrestricted,
        @NotNull @Size(max = 20) Set<@NotNull @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") @Size(max = 40) String> categories,
        @NotNull @PositiveOrZero Long expectedVersion) { }
