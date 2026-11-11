package com.streambid.inventory;

import com.streambid.offer.OfferRectangle;
import java.math.BigDecimal;
import java.util.UUID;

public record AvailablePlacement(UUID id, UUID channelId, String title, String position, int widthPx,
        int heightPx, int durationSeconds, BigDecimal minimumPrice, String currency, boolean negotiable, int capacity,
        OfferRectangle zone, boolean unrestrictedCategories, java.util.List<String> allowedCategories) {
    public AvailablePlacement {
        allowedCategories = java.util.List.copyOf(allowedCategories);
    }
}
