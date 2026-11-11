package com.streambid.channel;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChannelListingController {
    private final ChannelListingService listings;

    public ChannelListingController(ChannelListingService listings) {
        this.listings = listings;
    }

    @GetMapping("/api/channels/{id}/listing")
    public ResponseEntity<ChannelListing> read(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(listings.read(principal.getName(), id));
    }

    @PutMapping("/api/channels/{id}/listing")
    public ResponseEntity<ChannelListing> set(@PathVariable UUID id, Principal principal,
            @Valid @RequestBody ChannelListingRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(listings.set(principal.getName(), id, request.listed()));
    }
}
