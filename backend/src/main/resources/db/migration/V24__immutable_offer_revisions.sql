CREATE FUNCTION reject_offer_revision_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Offer revisions are immutable' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER immutable_offer_revisions BEFORE UPDATE OR DELETE ON ad_offer_revisions
    FOR EACH ROW EXECUTE FUNCTION reject_offer_revision_mutation();
