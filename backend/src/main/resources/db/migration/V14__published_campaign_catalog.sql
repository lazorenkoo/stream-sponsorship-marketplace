CREATE INDEX campaigns_published_catalog_index ON campaigns(published_at DESC, id DESC)
    WHERE status = 'PUBLISHED';
