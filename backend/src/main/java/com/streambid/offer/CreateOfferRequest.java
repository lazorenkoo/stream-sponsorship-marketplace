package com.streambid.offer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateOfferRequest(@NotNull UUID placementId, @NotNull @Valid OfferTerms terms, UUID campaignId) {
    public CreateOfferRequest(UUID placementId, OfferTerms terms) {
        this(placementId, terms, null);
    }
}
