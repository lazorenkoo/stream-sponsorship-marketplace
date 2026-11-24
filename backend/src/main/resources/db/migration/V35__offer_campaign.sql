ALTER TABLE ad_offers ADD COLUMN campaign_id UUID REFERENCES campaigns(id);
