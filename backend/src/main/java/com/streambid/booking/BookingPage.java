package com.streambid.booking;
import java.util.List;
import java.util.UUID;
public record BookingPage(List<BookingView> bookings, boolean hasMore, UUID nextCursor) {
    public BookingPage { bookings = List.copyOf(bookings); }
}
