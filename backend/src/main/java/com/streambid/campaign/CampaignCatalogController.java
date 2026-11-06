package com.streambid.campaign;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CampaignCatalogController {
    private final CampaignCatalogRepository campaigns;

    public CampaignCatalogController(CampaignCatalogRepository campaigns) {
        this.campaigns = campaigns;
    }

    @GetMapping("/api/catalog/campaigns")
    public ResponseEntity<CampaignCatalogPage> list(@RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Instant before, @RequestParam(required = false) UUID beforeId,
            @RequestParam(required = false) com.streambid.platform.StreamingPlatform platform) {
        if (limit < 1 || limit > 100 || (before == null) != (beforeId == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid catalog limit or cursor");
        }
        var rows = campaigns.list(before, beforeId, limit + 1, platform);
        boolean hasMore = rows.size() > limit;
        var page = rows.subList(0, Math.min(rows.size(), limit));
        var last = hasMore ? page.getLast() : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CampaignCatalogPage(page, hasMore,
                last == null ? null : last.publishedAt(), last == null ? null : last.id()));
    }

    @GetMapping("/api/catalog/campaigns/{id}")
    public ResponseEntity<PublishedCampaign> find(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(campaigns.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }
}
