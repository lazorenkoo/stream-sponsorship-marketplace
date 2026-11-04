package com.streambid.campaign;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import java.util.List;
import java.time.Instant;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CampaignController {
    private final CampaignDraftService drafts;

    public CampaignController(CampaignDraftService drafts) {
        this.drafts = drafts;
    }

    @GetMapping("/api/campaigns/{id}")
    public ResponseEntity<Campaign> find(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(drafts.findOwned(principal.getName(), id));
    }
    @GetMapping("/api/campaigns")
    public ResponseEntity<List<Campaign>> list(Principal principal, @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Instant before, @RequestParam(required = false) UUID beforeId,
            @RequestParam(required = false) CampaignStatus status) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(drafts.listOwned(principal.getName(), before, beforeId, limit, status));
    }
    @PutMapping("/api/campaigns/{id}")
    public ResponseEntity<Campaign> update(@PathVariable UUID id, Principal principal,
            @Valid @RequestBody CampaignDraftUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(drafts.update(principal.getName(), id, request));
    }
    @PostMapping("/api/campaigns/{id}/archive")
    public ResponseEntity<Campaign> archive(@PathVariable UUID id, Principal principal,
            @Valid @RequestBody CampaignArchiveRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(drafts.archive(principal.getName(), id, request));
    }
    @GetMapping("/api/campaigns/{id}/events")
    public ResponseEntity<List<CampaignEvent>> history(@PathVariable UUID id, Principal principal,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) Long beforeVersion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(drafts.history(principal.getName(), id, beforeVersion, limit));
    }
    @PostMapping("/api/campaigns")
    public ResponseEntity<Campaign> create(Principal principal, @Valid @RequestBody CampaignDraftRequest request) {
        var campaign = drafts.create(principal.getName(), request);
        return ResponseEntity.created(URI.create("/api/campaigns/" + campaign.id()))
                .cacheControl(CacheControl.noStore()).body(campaign);
    }
}
