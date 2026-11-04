package com.streambid.campaign;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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
    @PostMapping("/api/campaigns")
    public ResponseEntity<Campaign> create(Principal principal, @Valid @RequestBody CampaignDraftRequest request) {
        var campaign = drafts.create(principal.getName(), request);
        return ResponseEntity.created(URI.create("/api/campaigns/" + campaign.id()))
                .cacheControl(CacheControl.noStore()).body(campaign);
    }
}
