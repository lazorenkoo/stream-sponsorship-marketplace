package com.streambid.campaign;

import java.security.Principal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CampaignCandidateController {
    private final CampaignCandidateService candidates;

    public CampaignCandidateController(CampaignCandidateService candidates) {
        this.candidates = candidates;
    }

    @GetMapping("/api/campaigns/{id}/candidates")
    public ResponseEntity<CampaignCandidatePreview> preview(@PathVariable UUID id, Principal principal,
            @RequestParam(required = false) UUID after, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(candidates.preview(principal.getName(), id, after, limit));
    }
}
