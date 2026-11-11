CREATE TABLE ad_booking_cancellations (
    booking_id UUID PRIMARY KEY REFERENCES ad_bookings(id),
    version BIGINT NOT NULL CHECK (version > 0),
    actor_id UUID NOT NULL REFERENCES users(id),
    reason VARCHAR(500) NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 500),
    cancelled_at TIMESTAMPTZ NOT NULL
);
