package com.streambid.booking;
import java.util.UUID;
public record BookingView(UUID id, UUID offerId, long revision, UUID placementId, UUID channelId,
        BookingWindow window, String status, long version) { }
