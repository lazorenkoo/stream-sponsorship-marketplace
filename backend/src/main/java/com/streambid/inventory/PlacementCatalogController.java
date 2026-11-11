package com.streambid.inventory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PlacementCatalogController {
    private final PlacementCatalogRepository placements;

    public PlacementCatalogController(PlacementCatalogRepository placements) {
        this.placements = placements;
    }

    @GetMapping("/api/catalog/placements")
    public ResponseEntity<List<AvailablePlacement>> list(@RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) UUID after, @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String category) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Placement limit must be between 1 and 100");
        }
        if (category != null && (category.length() > 40 || !category.matches("[a-z0-9]+(-[a-z0-9]+)*"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid advertising category");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(placements.list(channelId, after, limit, category));
    }

    @GetMapping("/api/catalog/placements/{id}/hours")
    public ResponseEntity<List<PlacementHours>> hours(@PathVariable UUID id, @RequestParam(required = false) Instant after,
            @RequestParam(defaultValue = "20") int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hours limit must be between 1 and 100");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(placements.upcomingHours(id, after, limit)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @GetMapping("/api/catalog/placements/{id}")
    public ResponseEntity<AvailablePlacement> find(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(placements.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }
}
