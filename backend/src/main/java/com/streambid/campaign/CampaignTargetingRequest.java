package com.streambid.campaign;

import com.streambid.platform.StreamingPlatform;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CampaignTargetingRequest(@NotNull @Size(max = 3) Set<@NotNull StreamingPlatform> platforms,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
