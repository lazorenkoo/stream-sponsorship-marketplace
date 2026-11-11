package com.streambid.inventory;

import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.UUID;
import java.util.List;

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
    @GetMapping("/api/placements/{id}")
    public ResponseEntity<AdPlacement> find(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(placements.find(principal.getName(), id));
    }

    @GetMapping("/api/placements")
    public ResponseEntity<List<AdPlacement>> list(Principal principal, @RequestParam(required = false) UUID after,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(placements.list(principal.getName(), after, limit));
    }

    @PutMapping("/api/placements/{id}/activation")
    public ResponseEntity<AdPlacement> activate(@PathVariable UUID id, Principal principal,
            @Valid @RequestBody PlacementActivationRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(placements.setActive(principal.getName(), id, request));
    }
    @GetMapping("/api/placements/{id}/categories")
    public ResponseEntity<PlacementCategoryPolicy> categories(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(placements.categories(principal.getName(), id));
    }

    @PutMapping("/api/placements/{id}/categories")
    public ResponseEntity<PlacementCategoryPolicy> categories(@PathVariable UUID id, Principal principal,
            @Valid @RequestBody PlacementCategoryRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(placements.setCategories(principal.getName(), id, request));
    }

    @PutMapping("/api/placements/{id}")
    public ResponseEntity<AdPlacement> update(@PathVariable UUID id, Principal principal,
            @Valid @RequestBody AdPlacementUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(placements.update(principal.getName(), id, request));
    }
}
