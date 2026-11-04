package com.streambid.campaign;

import jakarta.validation.Valid;
import java.net.URI;
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

    @PostMapping("/api/campaigns")
    public ResponseEntity<Campaign> create(Principal principal, @Valid @RequestBody CampaignDraftRequest request) {
        var campaign = drafts.create(principal.getName(), request);
        return ResponseEntity.created(URI.create("/api/campaigns/" + campaign.id()))
                .cacheControl(CacheControl.noStore()).body(campaign);
    }
}
