ALTER TABLE ad_offers ADD COLUMN sent_at TIMESTAMPTZ;
UPDATE ad_offers SET sent_at = created_at
    WHERE status IN ('PENDING', 'COUNTERED', 'ACCEPTED', 'REJECTED', 'EXPIRED');
ALTER TABLE ad_offers ADD CONSTRAINT ad_offer_sent_time CHECK (sent_at IS NULL OR sent_at >= created_at);
