package com.streambid.campaign;

import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.UUID;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
public class CampaignParticipationController {
    private final CampaignParticipationService requests;

    public CampaignParticipationController(CampaignParticipationService requests) {
        this.requests = requests;
    }
    @GetMapping("/api/participations/{id}")
    public ResponseEntity<CampaignParticipation> find(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(requests.find(principal.getName(), id));
    }

    @GetMapping("/api/participations")
    public ResponseEntity<List<CampaignParticipation>> list(Principal principal,
            @RequestParam(required = false) UUID after, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(requests.list(principal.getName(), after, limit));
    }

    @PostMapping("/api/campaigns/{id}/applications")
    public ResponseEntity<CampaignParticipation> apply(@PathVariable UUID id, Principal principal,
            @Valid @RequestBody ParticipationRequest request) {
        return created(requests.create(principal.getName(), id, request.channelId(), ParticipationDirection.APPLICATION));
    }

    @PostMapping("/api/campaigns/{id}/invitations")
    public ResponseEntity<CampaignParticipation> invite(@PathVariable UUID id, Principal principal,
            @Valid @RequestBody ParticipationRequest request) {
        return created(requests.create(principal.getName(), id, request.channelId(), ParticipationDirection.INVITATION));
    }

    private ResponseEntity<CampaignParticipation> created(CampaignParticipation request) {
        return ResponseEntity.created(URI.create("/api/participations/" + request.id()))
                .cacheControl(CacheControl.noStore()).body(request);
    }
    @PostMapping("/api/participations/{id}/decision")
    public ResponseEntity<CampaignParticipation> decide(@PathVariable UUID id, Principal principal,
            @Valid @RequestBody ParticipationDecisionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(requests.decide(principal.getName(), id, request));
    }
}
