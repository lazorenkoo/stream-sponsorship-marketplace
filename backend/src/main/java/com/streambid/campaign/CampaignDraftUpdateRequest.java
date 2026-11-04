package com.streambid.campaign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CampaignDraftUpdateRequest(@NotBlank @Size(max = 120) String title,
        @NotNull @Size(max = 5000) String description, @NotNull @PositiveOrZero Long expectedVersion) {
}
