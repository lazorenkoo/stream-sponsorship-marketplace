package com.streambid.offer;

import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdOfferController {
    private final AdOfferService offers;

    public AdOfferController(AdOfferService offers) {
        this.offers = offers;
    }

    @PostMapping("/api/offers")
    public ResponseEntity<OfferView> create(Principal principal, @Valid @RequestBody CreateOfferRequest request) {
        var saved = offers.create(principal.getName(), request);
        return ResponseEntity.created(URI.create("/api/offers/" + saved.id())).cacheControl(CacheControl.noStore()).body(saved);
    }

    @PostMapping("/api/offers/{id}/send")
    public ResponseEntity<OfferView> send(Principal principal, @PathVariable UUID id,
            @Valid @RequestBody OfferVersionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(offers.send(principal.getName(), id, request));
    }

    @PostMapping("/api/offers/{id}/counter")
    public ResponseEntity<OfferView> counter(Principal principal, @PathVariable UUID id,
            @Valid @RequestBody CounterOfferRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(offers.counter(principal.getName(), id, request));
    }

    @PostMapping("/api/offers/{id}/close")
    public ResponseEntity<OfferView> close(Principal principal, @PathVariable UUID id,
            @Valid @RequestBody CloseOfferRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(offers.close(principal.getName(), id, request));
    }

    @GetMapping("/api/offers/{id}")
    public ResponseEntity<OfferView> find(Principal principal, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(offers.find(principal.getName(), id));
    }

    @GetMapping("/api/offers/{id}/revisions")
    public ResponseEntity<List<OfferRevisionView>> revisions(Principal principal, @PathVariable UUID id,
            @RequestParam(required = false) Long beforeRevision, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(offers.revisions(principal.getName(), id, beforeRevision, limit));
    }

    @GetMapping("/api/offers/{id}/events")
    public ResponseEntity<List<OfferEventView>> events(Principal principal, @PathVariable UUID id,
            @RequestParam(required = false) Long beforeVersion, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(offers.events(principal.getName(), id, beforeVersion, limit));
    }

    @GetMapping("/api/offers")
    public ResponseEntity<List<OfferView>> list(Principal principal, @RequestParam(required = false) UUID after,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(offers.list(principal.getName(), after, limit));
    }
}
