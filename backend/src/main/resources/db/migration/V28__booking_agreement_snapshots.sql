CREATE FUNCTION validate_booking_agreement() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND
        ROW(NEW.id, NEW.offer_id, NEW.revision, NEW.placement_id, NEW.channel_id, NEW.starts_at, NEW.ends_at,
            NEW.rectangle_x, NEW.rectangle_y, NEW.rectangle_width, NEW.rectangle_height, NEW.created_at)
        IS DISTINCT FROM
        ROW(OLD.id, OLD.offer_id, OLD.revision, OLD.placement_id, OLD.channel_id, OLD.starts_at, OLD.ends_at,
            OLD.rectangle_x, OLD.rectangle_y, OLD.rectangle_width, OLD.rectangle_height, OLD.created_at) THEN
        RAISE EXCEPTION 'Booking agreement is immutable' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'INSERT' AND NOT EXISTS (
        SELECT 1 FROM ad_offers o JOIN ad_offer_revisions r ON r.offer_id = o.id AND r.revision = o.accepted_revision
        JOIN ad_placements p ON p.id = o.placement_id
        WHERE o.id = NEW.offer_id AND o.status = 'ACCEPTED' AND o.accepted_revision = NEW.revision
          AND o.current_revision = NEW.revision AND p.id = NEW.placement_id AND p.channel_id = NEW.channel_id
          AND r.mode = 'SCHEDULED' AND r.starts_at = NEW.starts_at
          AND r.starts_at + r.duration_seconds * INTERVAL '1 second' = NEW.ends_at
          AND r.rectangle_x = NEW.rectangle_x AND r.rectangle_y = NEW.rectangle_y
          AND r.rectangle_width = NEW.rectangle_width AND r.rectangle_height = NEW.rectangle_height
    ) THEN
        RAISE EXCEPTION 'Booking must match the accepted offer revision' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER booking_agreement_snapshot BEFORE INSERT OR UPDATE ON ad_bookings
    FOR EACH ROW EXECUTE FUNCTION validate_booking_agreement();
