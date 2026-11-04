package com.streambid.campaign;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CampaignTargetingController {
    private final CampaignTargetingService targeting;

    public CampaignTargetingController(CampaignTargetingService targeting) {
        this.targeting = targeting;
    }

    @GetMapping("/api/campaigns/{id}/targeting")
    public ResponseEntity<CampaignTargetingView> read(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(targeting.read(principal.getName(), id));
    }

    @PutMapping("/api/campaigns/{id}/targeting")
    public ResponseEntity<CampaignTargetingView> replace(@PathVariable UUID id, Principal principal,
            @Valid @RequestBody CampaignTargetingRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(targeting.replace(principal.getName(), id, request));
    }
}
