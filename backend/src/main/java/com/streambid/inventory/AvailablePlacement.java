package com.streambid.inventory;
import java.math.BigDecimal;
import java.util.UUID;

public record AvailablePlacement(UUID id, UUID channelId, String title, String position, int widthPx,
        int heightPx, int durationSeconds, BigDecimal minimumPrice, String currency, boolean negotiable, int capacity) { }
