package com.streambid.campaign;

import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CampaignParticipationController {
    private final CampaignParticipationService requests;

    public CampaignParticipationController(CampaignParticipationService requests) {
        this.requests = requests;
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
}
