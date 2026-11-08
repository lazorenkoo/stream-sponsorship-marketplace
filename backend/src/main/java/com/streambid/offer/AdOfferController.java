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

    @GetMapping("/api/offers/{id}")
    public ResponseEntity<OfferView> find(Principal principal, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(offers.find(principal.getName(), id));
    }

    @GetMapping("/api/offers")
    public ResponseEntity<List<OfferView>> list(Principal principal, @RequestParam(required = false) UUID after,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(offers.list(principal.getName(), after, limit));
    }
}
