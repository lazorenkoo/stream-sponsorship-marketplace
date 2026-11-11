package com.streambid.booking;
import java.util.List;
public record ReservationPage(List<BookingWindow> reservations, boolean hasMore, String nextCursor) {
    public ReservationPage { reservations = List.copyOf(reservations); }
}
