package com.streambid.inventory;

import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdPlacementController {
    private final AdPlacementService placements;

    public AdPlacementController(AdPlacementService placements) {
        this.placements = placements;
    }

    @PostMapping("/api/placements")
    public ResponseEntity<AdPlacement> create(Principal principal, @Valid @RequestBody AdPlacementRequest request) {
        var saved = placements.create(principal.getName(), request);
        return ResponseEntity.created(URI.create("/api/placements/" + saved.id()))
                .cacheControl(CacheControl.noStore()).body(saved);
    }
}
