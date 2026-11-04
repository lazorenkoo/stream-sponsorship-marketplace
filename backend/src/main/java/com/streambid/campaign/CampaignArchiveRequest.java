package com.streambid.campaign;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CampaignArchiveRequest(@NotNull @PositiveOrZero Long expectedVersion) {
}
